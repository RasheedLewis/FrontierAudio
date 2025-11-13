package com.example.frontieraudio.jarvis.nova

import android.util.Base64
import com.example.frontieraudio.core.logging.FrontierLogger
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.async.SdkPublisher
import software.amazon.awssdk.http.Protocol
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.services.bedrockruntime.model.BidirectionalOutputPayloadPart
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamInput
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamOutput
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamRequest
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamResponse
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamResponseHandler

@Singleton
class NovaStreamingClient @Inject constructor(
    private val config: JarvisNovaConfig,
    private val logger: FrontierLogger,
    private val credentialsProvider: AwsCredentialsProvider
) : Closeable {

    private val httpClient by lazy {
        NettyNioAsyncHttpClient.builder()
            .maxConcurrency(32)
            .readTimeout(Duration.ofSeconds(180))
            .protocol(Protocol.HTTP2)
            .build()
    }

    private val runtimeClient: BedrockRuntimeAsyncClient by lazy {
        BedrockRuntimeAsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(config.region)
            .httpClient(httpClient)
            .build()
    }

    private val closed = AtomicBoolean(false)

    fun openSession(request: NovaSessionRequest, listener: NovaStreamListener): NovaSession {
        check(!closed.get()) { "NovaStreamingClient is already closed." }

        val dispatchingListener = SessionDispatchingListener(listener)
        val requestPublisher = NovaRequestPublisher()
        val responseHandler = NovaResponseHandler(dispatchingListener)
        val invokeRequest = InvokeModelWithBidirectionalStreamRequest.builder()
            .modelId(config.modelId)
            .build()

        val completion = try {
            runtimeClient.invokeModelWithBidirectionalStream(invokeRequest, requestPublisher, responseHandler)
        } catch (throwable: Throwable) {
            listener.onError(throwable)
            throw throwable
        }

        val session = NovaSession(
            sessionId = request.sessionId,
            request = request,
            publisher = requestPublisher,
            completion = completion,
            listener = dispatchingListener
        )
        session.sendSessionStart()
        return session
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                runtimeClient.close()
            } catch (t: Exception) {
                logger.e("Error closing BedrockRuntime client", throwable = t)
            }
            try {
                httpClient.close()
            } catch (t: Exception) {
                logger.e("Error closing Netty HTTP client", throwable = t)
            }
        }
    }

    inner class NovaSession internal constructor(
        val sessionId: String,
        private val request: NovaSessionRequest,
        private val publisher: NovaRequestPublisher,
        private val completion: CompletableFuture<Void>,
        private val listener: NovaStreamListener
    ) : Closeable {

        private val scheduler: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "jarvis-nova-$sessionId").apply {
                    isDaemon = true
                }
            }

        private val lastActivityMs = AtomicLong(System.currentTimeMillis())
        private val ended = AtomicBoolean(false)
        private val closedNotified = AtomicBoolean(false)

        private val heartbeatTask: ScheduledFuture<*>? =
            if (config.heartbeatIntervalMs > 0) {
                scheduler.scheduleAtFixedRate(
                    { sendHeartbeatInternal() },
                    config.heartbeatIntervalMs,
                    config.heartbeatIntervalMs,
                    TimeUnit.MILLISECONDS
                )
            } else {
                null
            }

        private val timeoutMonitor: ScheduledFuture<*>? =
            if (config.sessionTimeoutMs > 0) {
                val cadence = (config.sessionTimeoutMs / 2).coerceAtLeast(1_000L)
                scheduler.scheduleAtFixedRate(
                    { checkSessionTimeout() },
                    config.sessionTimeoutMs,
                    cadence,
                    TimeUnit.MILLISECONDS
                )
            } else {
                null
            }

        init {
            completion.whenComplete { _, throwable ->
                ended.set(true)
                cleanupScheduler()
                if (throwable != null) {
                    logger.e("Nova session $sessionId terminated with error", throwable = throwable)
                    listener.onError(throwable)
                }
                signalClosed()
            }
        }

        fun sendUserText(message: String) {
            val payload = JSONObject()
                .put("event", JSONObject().put("textInput", JSONObject().put("content", message)))
            enqueuePayload(payload)
        }

        fun sendPcmAudioChunk(
            audio: ByteArray,
            sampleRateHz: Int,
            channelCount: Int
        ) {
            val audioPayload = JSONObject()
                .put(
                    "audioFormat",
                    JSONObject()
                        .put("type", "pcm16")
                        .put("sampleRateHz", sampleRateHz)
                        .put("channels", channelCount)
                )
                .put(
                    "content",
                    Base64.encodeToString(audio, Base64.NO_WRAP)
                )
            val payload = JSONObject()
                .put(
                    "event",
                    JSONObject().put(
                        "audioInput",
                        JSONObject()
                            .put("sessionId", sessionId)
                            .put("audio", audioPayload)
                    )
                )
            enqueuePayload(payload)
        }

        fun sendHeartbeat() {
            sendHeartbeatInternal()
        }

        fun isClosed(): Boolean = ended.get()

        override fun close() {
            endSessionInternal(null)
        }

        fun close(reason: String?) {
            endSessionInternal(reason)
        }

        internal fun sendSessionStart() {
            val inference = JSONObject()
                .put("maxTokens", request.inferenceConfiguration.maxTokens)
                .put("temperature", request.inferenceConfiguration.temperature)
                .put("topP", request.inferenceConfiguration.topP)

            val sessionStart = JSONObject()
                .put("sessionId", sessionId)
                .put("systemPrompt", request.systemPrompt)
                .put("inferenceConfiguration", inference)

            if (request.metadata.isNotEmpty()) {
                try {
                    sessionStart.put("metadata", JSONObject(request.metadata))
                } catch (ignored: Exception) {
                    logger.w("Unable to encode metadata for Nova session start; continuing without metadata.")
                }
            }

            val payload = JSONObject().put(
                "event",
                JSONObject().put("sessionStart", sessionStart)
            )

            enqueuePayload(payload)
        }

        private fun enqueuePayload(json: JSONObject) {
            if (ended.get()) {
                logger.w("Nova session $sessionId is closed; dropping outbound payload.")
                return
            }
            val bytes = json.toString()
            publisher.offer(
                InvokeModelWithBidirectionalStreamInput.chunkBuilder()
                    .bytes(SdkBytes.fromUtf8String(bytes))
                    .build()
            )
            lastActivityMs.set(System.currentTimeMillis())
        }

        private fun sendHeartbeatInternal() {
            val heartbeat = JSONObject()
                .put("event", JSONObject().put("sessionHeartbeat", JSONObject().put("sessionId", sessionId)))
            enqueuePayload(heartbeat)
        }

        private fun checkSessionTimeout() {
            if (ended.get()) return
            val idleMs = System.currentTimeMillis() - lastActivityMs.get()
            if (idleMs >= config.sessionTimeoutMs) {
                logger.w("Nova session $sessionId idle for $idleMs ms, ending session.")
                endSessionInternal("timeout")
            }
        }

        private fun endSessionInternal(reason: String?) {
            if (!ended.compareAndSet(false, true)) return

            val endPayload = JSONObject().apply {
                put(
                    "event",
                    JSONObject().put(
                        "sessionEnd",
                        JSONObject().apply {
                            put("sessionId", sessionId)
                            reason?.let { put("reason", it) }
                        }
                    )
                )
            }

            publisher.offer(
                InvokeModelWithBidirectionalStreamInput.chunkBuilder()
                    .bytes(SdkBytes.fromUtf8String(endPayload.toString()))
                    .build()
            )
            publisher.complete()
            cleanupScheduler()
            signalClosed()
        }

        private fun cleanupScheduler() {
            heartbeatTask?.cancel(false)
            timeoutMonitor?.cancel(false)
            scheduler.shutdownNow()
        }

        private fun signalClosed() {
            if (closedNotified.compareAndSet(false, true)) {
                listener.onSessionClosed()
            }
        }
    }

    private inner class NovaResponseHandler(
        private val listener: NovaStreamListener
    ) : InvokeModelWithBidirectionalStreamResponseHandler {

        override fun responseReceived(response: InvokeModelWithBidirectionalStreamResponse) {
            listener.onSessionEstablished(response.responseMetadata().requestId())
        }

        override fun onEventStream(publisher: SdkPublisher<InvokeModelWithBidirectionalStreamOutput>) {
            val visitor = InvokeModelWithBidirectionalStreamResponseHandler.Visitor.builder()
                .onChunk { event: BidirectionalOutputPayloadPart ->
                    val payload = String(event.bytes().asByteArray(), StandardCharsets.UTF_8)
                    listener.onEventPayload(payload)
                }
                .onDefault { output: InvokeModelWithBidirectionalStreamOutput ->
                    logger.d("Unhandled Nova stream event type: ${output.sdkEventType()}")
                }
                .build()

            publisher.subscribe { output ->
                output.accept(visitor)
            }
        }

        override fun exceptionOccurred(throwable: Throwable) {
            listener.onError(throwable)
        }

        override fun complete() = Unit
    }

    private class SessionDispatchingListener(
        private val delegate: NovaStreamListener
    ) : NovaStreamListener {
        private val errorSent = AtomicBoolean(false)
        private val closedSent = AtomicBoolean(false)

        override fun onSessionEstablished(requestId: String?) {
            delegate.onSessionEstablished(requestId)
        }

        override fun onEventPayload(eventJson: String) {
            delegate.onEventPayload(eventJson)
        }

        override fun onError(throwable: Throwable) {
            if (errorSent.compareAndSet(false, true)) {
                delegate.onError(throwable)
            }
        }

        override fun onSessionClosed() {
            if (closedSent.compareAndSet(false, true)) {
                delegate.onSessionClosed()
            }
        }
    }

    internal class NovaRequestPublisher :
        Publisher<InvokeModelWithBidirectionalStreamInput>, Subscription {

        private val lock = Any()
        private val queue = ArrayDeque<InvokeModelWithBidirectionalStreamInput>()
        private var subscriber: Subscriber<in InvokeModelWithBidirectionalStreamInput>? = null
        private var cancelled = false
        private var completed = false

        override fun subscribe(s: Subscriber<in InvokeModelWithBidirectionalStreamInput>) {
            val shouldSubscribe = synchronized(lock) {
                if (subscriber != null) {
                    false
                } else {
                    subscriber = s
                    true
                }
            }
            if (!shouldSubscribe) {
                s.onError(IllegalStateException("NovaRequestPublisher supports only a single subscriber."))
                return
            }
            s.onSubscribe(this)
            drain()
        }

        override fun request(n: Long) {
            if (n <= 0) return
            drain()
        }

        override fun cancel() {
            val toComplete = synchronized(lock) {
                cancelled = true
                queue.clear()
                subscriber
            }
            toComplete?.onComplete()
        }

        fun offer(event: InvokeModelWithBidirectionalStreamInput) {
            val shouldDrain = synchronized(lock) {
                if (cancelled || completed) {
                    false
                } else {
                    queue.addLast(event)
                    subscriber != null
                }
            }
            if (shouldDrain) drain()
        }

        fun complete() {
            val shouldDrain = synchronized(lock) {
                if (completed) {
                    false
                } else {
                    completed = true
                    subscriber != null
                }
            }
            if (shouldDrain) drain()
        }

        private fun drain() {
            val currentSubscriber = synchronized(lock) { subscriber }
            if (currentSubscriber == null) return
            while (true) {
                val next = synchronized(lock) {
                    if (cancelled || queue.isEmpty()) null else queue.removeFirst()
                } ?: break
                currentSubscriber.onNext(next)
            }
            val shouldComplete = synchronized(lock) {
                completed && queue.isEmpty() && !cancelled
            }
            if (shouldComplete) {
                currentSubscriber.onComplete()
            }
        }
    }
}


