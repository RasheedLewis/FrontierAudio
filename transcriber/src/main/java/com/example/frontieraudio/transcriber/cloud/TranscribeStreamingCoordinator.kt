package com.example.frontieraudio.transcriber.cloud

import com.example.frontieraudio.core.logging.FrontierLogger
import com.example.frontieraudio.transcriber.audio.AudioWindowListener
import com.example.frontieraudio.transcriber.verification.SpeakerVerificationState
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Bridges audio windows into Amazon Transcribe Streaming.
 */
class TranscribeStreamingCoordinator(
    private val service: TranscribeStreamingService,
    private val verificationState: StateFlow<SpeakerVerificationState>,
    private val logger: FrontierLogger,
    private val scope: CoroutineScope
) : AudioWindowListener, Closeable {

    private val activeHandle = AtomicReference<TranscribeStreamingService.StreamingHandle?>()
    private val lastVerificationState = AtomicReference<SpeakerVerificationState?>(null)
    private val streamingEnabledState = MutableStateFlow(false)
    private var verificationJob: Job? = null
    private var streamingJob: Job? = null

    fun start() {
        verificationJob = scope.launch {
            verificationState.collect { state ->
                lastVerificationState.set(state)
            }
        }
        streamingJob = scope.launch {
            streamingEnabledState.collect { enabled ->
                if (enabled) {
                    ensureSession(lastVerificationState.get())
                } else {
                    stopSession()
                }
            }
        }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        if (streamingEnabledState.value == enabled) return
        streamingEnabledState.value = enabled
    }

    fun streamingEnabled(): StateFlow<Boolean> = streamingEnabledState.asStateFlow()

    fun currentStreamingEnabled(): Boolean = streamingEnabledState.value

    override fun onWindow(data: ByteArray, timestampMillis: Long) {
        val handle = activeHandle.get() ?: return
        if (!handle.trySendAudioChunk(data.copyOf())) {
            scope.launch {
                runCatching {
                    handle.sendAudioChunk(data.copyOf())
                }.onFailure { throwable ->
                    logger.e("Failed to stream audio chunk to Transcribe", throwable = throwable)
                }
            }
        }
    }

    override fun close() {
        verificationJob?.cancel()
        verificationJob = null
        streamingJob?.cancel()
        streamingJob = null
        stopSession()
    }

    private fun ensureSession(state: SpeakerVerificationState?) {
        if (activeHandle.get() != null) return

        scope.launch {
            runCatching {
                service.startSession()
            }.onSuccess { handle ->
                activeHandle.set(handle)
                logger.i("Started Transcribe streaming session %s", handle.sessionId)
            }.onFailure { throwable ->
                logger.e("Unable to start Transcribe streaming session", throwable = throwable)
            }
        }
    }

    private fun stopSession() {
        val handle = activeHandle.getAndSet(null) ?: return
        scope.launch {
            runCatching { handle.close() }
                .onFailure { throwable ->
                    logger.w(
                        "Error closing Transcribe streaming session %s: %s",
                        handle.sessionId,
                        throwable.message ?: "unknown"
                    )
                }
        }
    }
}


