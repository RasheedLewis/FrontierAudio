package com.example.frontieraudio.transcriber.cloud

import com.example.frontieraudio.core.logging.FrontierLogger
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asPublisher
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream
import software.amazon.awssdk.services.transcribestreaming.model.ItemType
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptResultStream

class TranscribeStreamingService(
    private val config: TranscribeStreamingConfig,
    private val logger: FrontierLogger,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO
) : Closeable {

    private val transcriptEvents =
        MutableSharedFlow<TranscribeTranscript>(
            replay = 0,
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val activeSessions = mutableMapOf<String, StreamingSession>()

    fun transcripts(): Flow<TranscribeTranscript> = transcriptEvents.asSharedFlow()

    suspend fun startSession(
        sessionId: String = UUID.randomUUID().toString(),
        vocabularyName: String? = config.vocabularyName,
        customCredentialsProvider: AwsCredentialsProvider? = null
    ): StreamingHandle {
        if (!config.enabled) {
            throw IllegalStateException(
                "Transcribe streaming is disabled in the current configuration."
            )
        }

        val resolvedCredentials = customCredentialsProvider ?: config.credentialsProviderFactory()
        val client = TranscribeStreamingAsyncClient.builder()
            .credentialsProvider(resolvedCredentials)
            .region(Region.of(config.region))
            .build()

        val audioChannel = Channel<ByteArray>(capacity = Channel.BUFFERED)
        val session = StreamingSession(sessionId, client, audioChannel)
        activeSessions[sessionId] = session

        scope.launch(ioDispatcher) {
            val audioPublisher = audioChannel.consumeAsFlow()
                .map { bytes ->
                    AudioEvent.builder()
                        .audioChunk(SdkBytes.fromByteArray(bytes))
                        .build() as AudioStream
                }
                .asPublisher()

            val requestBuilder = StartStreamTranscriptionRequest.builder()
                .languageCode(parseLanguageCode(config.languageCode))
                .mediaSampleRateHertz(config.sampleRateHz)
                .mediaEncoding(config.mediaEncoding.toAwsEncoding())
                .sessionId(sessionId)

            vocabularyName?.let { requestBuilder.vocabularyName(it) }

            val responseHandler = createResponseHandler(sessionId)

            try {
                client
                    .startStreamTranscription(
                        requestBuilder.build(),
                        audioPublisher,
                        responseHandler
                    )
                    .join()
            } catch (cancellation: CancellationException) {
                logger.w("Transcribe streaming session %s cancelled", sessionId)
            } catch (completion: CompletionException) {
                logger.e(
                    "Transcribe streaming session %s failed",
                    sessionId,
                    throwable = completion.cause ?: completion
                )
            } catch (t: Throwable) {
                logger.e("Transcribe streaming session %s failed", sessionId, throwable = t)
            } finally {
                runCatching { audioChannel.close() }
                cleanupSession(sessionId)
            }
        }

        return StreamingHandle(
            sessionId = sessionId,
            audioChannel = audioChannel,
            onClose = { runCatching { audioChannel.close() } }
        )
    }

    private fun createResponseHandler(sessionId: String): StartStreamTranscriptionResponseHandler =
        StartStreamTranscriptionResponseHandler.builder()
            .subscriber(
                StartStreamTranscriptionResponseHandler.Visitor.builder()
                    .onTranscriptEvent { event ->
                        scope.launch {
                            runCatching { publishTranscript(sessionId, event) }
                                .onFailure { throwable ->
                                    logger.e(
                                        "Failed to process transcript event for session %s",
                                        sessionId,
                                        throwable = throwable
                                    )
                                }
                        }
                    }
                    .onDefault { event ->
                        logger.d(
                            "Unhandled transcript result event type %s for session %s",
                            event.sdkEventType(),
                            sessionId
                        )
                    }
                    .build()
            )
            .build()

    private suspend fun publishTranscript(sessionId: String, event: TranscriptEvent) {
        val transcript = event.transcript() ?: return
        val timestampMillis = System.currentTimeMillis()

        transcript.results().orEmpty().forEachIndexed { index, result ->
            val alternative = result.alternatives().orEmpty().firstOrNull()
            val text = alternative?.transcript().orEmpty()
            if (text.isBlank()) return@forEachIndexed

            val items =
                alternative?.items().orEmpty().map { item ->
                    TranscribeTranscript.TranscriptItem(
                        content = item.content().orEmpty(),
                        startTimeSeconds = item.startTime(),
                        endTimeSeconds = item.endTime(),
                        type = item.type().toLocalItemType()
                    )
                }

            val transcriptResult =
                TranscribeTranscript(
                    sessionId = sessionId,
                    text = text,
                    isPartial = result.isPartial() ?: false,
                    startTimeSeconds = result.startTime(),
                    endTimeSeconds = result.endTime(),
                    resultId = result.resultId(),
                    channelId = result.channelId(),
                    speakerId = result.channelId(),
                    items = items,
                    sequenceNumber = result.resultId()?.hashCode()?.toLong() ?: index.toLong(),
                    rawEventTimestampMillis = timestampMillis
                )

            logger.d(
                "Transcribe transcript event (%s): %s",
                if (transcriptResult.isPartial) "partial" else "final",
                transcriptResult.text
            )
            transcriptEvents.emit(transcriptResult)
        }
    }

    private fun cleanupSession(sessionId: String) {
        activeSessions.remove(sessionId)?.close()
    }

    override fun close() {
        activeSessions.values.forEach { runCatching { it.close() } }
        activeSessions.clear()
    }

    private fun parseLanguageCode(value: String): LanguageCode =
        runCatching { LanguageCode.fromValue(value) }.getOrDefault(LanguageCode.EN_US)

    private fun StreamingMediaEncoding.toAwsEncoding(): MediaEncoding = when (this) {
        StreamingMediaEncoding.PCM -> MediaEncoding.PCM
    }

    private fun ItemType?.toLocalItemType(): TranscribeTranscript.ItemType = when (this) {
        ItemType.PRONUNCIATION -> TranscribeTranscript.ItemType.Pronunciation
        ItemType.PUNCTUATION -> TranscribeTranscript.ItemType.Punctuation
        else -> TranscribeTranscript.ItemType.Unknown
    }

    data class StreamingHandle
    internal constructor(
        val sessionId: String,
        private val audioChannel: Channel<ByteArray>,
        private val onClose: suspend () -> Unit
    ) {
        suspend fun sendAudioChunk(bytes: ByteArray) {
            audioChannel.send(bytes)
        }

        fun trySendAudioChunk(bytes: ByteArray): Boolean =
            audioChannel.trySend(bytes).isSuccess

        suspend fun close() {
            onClose()
        }
    }

    private class StreamingSession(
        val sessionId: String,
        private val client: TranscribeStreamingAsyncClient,
        private val audioChannel: Channel<ByteArray>
    ) : Closeable {
        override fun close() {
            runCatching { audioChannel.close() }
            runCatching { client.close() }
        }
    }
}

