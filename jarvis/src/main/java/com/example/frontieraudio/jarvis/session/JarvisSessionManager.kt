package com.example.frontieraudio.jarvis.session

import android.util.Base64
import com.example.frontieraudio.core.logging.FrontierLogger
import com.example.frontieraudio.jarvis.nova.JarvisNovaConfig
import com.example.frontieraudio.jarvis.nova.NovaSessionRequest
import com.example.frontieraudio.jarvis.nova.NovaStreamListener
import com.example.frontieraudio.jarvis.nova.NovaStreamingClient
import com.example.frontieraudio.jarvis.ui.JarvisLinkStatus
import com.example.frontieraudio.jarvis.ui.JarvisUiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

@Singleton
class JarvisSessionManager @Inject constructor(
    private val novaStreamingClient: NovaStreamingClient,
    private val novaConfig: JarvisNovaConfig,
    private val audioPlayer: NovaAudioPlayer,
    private val logger: FrontierLogger
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var activeSession: NovaStreamingClient.NovaSession? = null
    private var audioPumpJob: Job? = null
    private var speechResetJob: Job? = null
    private var currentSampleRate = 16_000
    private var currentChannelCount = 1

    private var audioFrames: Channel<AudioFrame> = newAudioChannel()

    fun startSession(sampleRateHz: Int, channelCount: Int) {
        if (activeSession != null) {
            logger.d("Jarvis session already active; ignoring start request")
            return
        }
        currentSampleRate = sampleRateHz
        currentChannelCount = channelCount

        val request = NovaSessionRequest(
            systemPrompt = systemPrompt,
            metadata = mapOf(
                "module" to "jarvis",
                "platform" to "android",
                "sampleRateHz" to sampleRateHz,
                "channelCount" to channelCount
            )
        )
        _uiState.update {
            it.copy(
                isEnabled = true,
                linkStatus = JarvisLinkStatus.CONNECTING,
                sessionId = request.sessionId,
                statusMessage = "Connecting to Nova…",
                isListening = true
            )
        }

        try {
            val listener = createStreamListener()
            val session = novaStreamingClient.openSession(request, listener)
            activeSession = session
            startAudioPump(session)
        } catch (t: Throwable) {
            logger.e("Failed to open Nova session", throwable = t)
            _uiState.update {
                it.copy(
                    isEnabled = false,
                    linkStatus = JarvisLinkStatus.DISCONNECTED,
                    statusMessage = "Connection failed: ${t.message ?: "unknown error"}",
                    isListening = false,
                    isSpeaking = false,
                    sessionId = null
                )
            }
        }
    }

    fun stopSession(reason: String? = null) {
        val session = activeSession
        activeSession = null
        audioPumpJob?.cancel()
        audioPumpJob = null
        resetAudioChannel()
        audioPlayer.stop()
        speechResetJob?.cancel()
        speechResetJob = null
        session?.close(reason)
        _uiState.update {
            JarvisUiState(
                statusMessage = reason?.let { "Session closed: $it" }
            )
        }
    }

    fun onAudioFrame(data: ByteArray, sampleRateHz: Int, channelCount: Int, timestampMillis: Long) {
        if (activeSession == null) return
        val safeSampleRate = sampleRateHz.takeIf { it > 0 } ?: currentSampleRate
        val safeChannels = channelCount.takeIf { it > 0 } ?: currentChannelCount
        currentSampleRate = safeSampleRate
        currentChannelCount = safeChannels

        val rmsLevel = calculateRmsLevel(data)
        _uiState.update { it.copy(vuLevel = rmsLevel, isListening = true) }

        val frame = AudioFrame(
            data = data.copyOf(),
            sampleRateHz = safeSampleRate,
            channelCount = safeChannels,
            timestampMillis = timestampMillis
        )
        val offered = audioFrames.trySend(frame)
        if (offered.isFailure) {
            logger.w("Jarvis audio frame dropped due to backpressure")
            _uiState.update { it.copy(linkStatus = JarvisLinkStatus.DEGRADED) }
        }
    }

    private fun startAudioPump(session: NovaStreamingClient.NovaSession) {
        audioPumpJob?.cancel()
        audioPumpJob = scope.launch {
            for (frame in audioFrames) {
                try {
                    session.sendPcmAudioChunk(
                        audio = frame.data,
                        sampleRateHz = frame.sampleRateHz,
                        channelCount = frame.channelCount
                    )
                    _uiState.update { state ->
                        if (state.linkStatus == JarvisLinkStatus.DEGRADED) {
                            state.copy(linkStatus = JarvisLinkStatus.CONNECTED)
                        } else {
                            state
                        }
                    }
                } catch (t: Throwable) {
                    logger.e("Failed to stream audio chunk to Nova", throwable = t)
                    handleStreamingError(t)
                    break
                }
            }
        }
    }

    private fun createStreamListener(): NovaStreamListener = object : NovaStreamListener {
        override fun onSessionEstablished(requestId: String?) {
            _uiState.update {
                it.copy(
                    linkStatus = JarvisLinkStatus.CONNECTED,
                    statusMessage = "Secure uplink established",
                    connectionLatencyMs = null
                )
            }
            logger.i("Jarvis Nova session established requestId=%s", requestId)
        }

        override fun onEventPayload(eventJson: String) {
            try {
                val payload = JSONObject(eventJson)
                val event = payload.optJSONObject("event") ?: return
                when {
                    event.has("textOutput") -> handleTextOutput(event.getJSONObject("textOutput"))
                    event.has("audioOutput") -> handleAudioOutput(event.getJSONObject("audioOutput"))
                    event.has("contentEnd") -> handleContentEnd()
                }
            } catch (ex: JSONException) {
                logger.w("Failed to parse Nova event payload: %s", eventJson)
            }
        }

        override fun onError(throwable: Throwable) {
            handleStreamingError(throwable)
        }

        override fun onSessionClosed() {
            logger.i("Nova session closed by remote")
            stopSession("remote_end")
        }
    }

    private fun handleTextOutput(textOutput: JSONObject) {
        val content = textOutput.optString("content")
        if (content.isNotBlank()) {
            _uiState.update { it.copy(statusMessage = content) }
        }
    }

    private fun handleAudioOutput(audioOutput: JSONObject) {
        val content = audioOutput.optString("content")
        if (content.isNullOrBlank()) return
        val audioBytes = try {
            Base64.decode(content, Base64.NO_WRAP)
        } catch (t: Throwable) {
            logger.e("Failed to decode Nova audio output", throwable = t)
            return
        }
        val audioFormat = audioOutput.optJSONObject("audioFormat")
        val sampleRate = audioFormat?.optInt("sampleRateHz")?.takeIf { it > 0 } ?: currentSampleRate
        val channels = audioFormat?.optInt("channels")?.takeIf { it > 0 } ?: currentChannelCount

        _uiState.update { it.copy(isSpeaking = true) }
        speechResetJob?.cancel()
        speechResetJob = scope.launch {
            audioPlayer.playPcm(audioBytes, sampleRate, channels)
            _uiState.update { it.copy(isSpeaking = false) }
        }
    }

    private fun handleContentEnd() {
        _uiState.update { it.copy(isSpeaking = false) }
    }

    private fun handleStreamingError(throwable: Throwable) {
        logger.e("Jarvis streaming error", throwable = throwable)
        _uiState.update {
            it.copy(
                linkStatus = JarvisLinkStatus.DEGRADED,
                statusMessage = "Connection issue: ${throwable.message ?: "unknown"}"
            )
        }
    }

    private fun calculateRmsLevel(bytes: ByteArray): Float {
        if (bytes.isEmpty()) return 0f
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val sample = (((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt())
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            samples += 1
            i += 2
        }
        if (samples == 0) return 0f
        val rms = sqrt(sumSquares / samples)
        return min(1f, max(0f, rms.toFloat()))
    }

    private fun newAudioChannel(): Channel<AudioFrame> =
        Channel(capacity = 6, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun resetAudioChannel() {
        audioFrames.close()
        audioFrames = newAudioChannel()
    }

    private val systemPrompt: String = buildString {
        appendLine("You are Frontier Audio Jarvis, a mission-grade voice assistant for frontline operators.")
        appendLine("Responsibilities:")
        appendLine("- Deliver accurate, verifiable answers with concise steps for hazardous field tasks.")
        appendLine("- Maintain operator privacy; ignore background speakers and request clarification if uncertain.")
        appendLine("- Reference recent transcript context with timestamps or GPS metadata when helpful.")
        appendLine("- Flag compliance or safety risks explicitly and recommend escalation paths when needed.")
        appendLine("- Keep responses direct, professional, and optimized for audio playback.")
        append("If information is incomplete, ask for the missing detail instead of guessing.")
    }

    private data class AudioFrame(
        val data: ByteArray,
        val sampleRateHz: Int,
        val channelCount: Int,
        val timestampMillis: Long
    )
}
