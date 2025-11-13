package com.example.frontieraudio.jarvis.session

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.frontieraudio.core.logging.FrontierLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.delay

@Singleton
class NovaAudioPlayer @Inject constructor(
    private val logger: FrontierLogger
) {

    private var audioTrack: AudioTrack? = null
    private var currentConfig: PlaybackConfig? = null

    suspend fun playPcm(data: ByteArray, sampleRateHz: Int, channelCount: Int) {
        try {
            ensureAudioTrack(sampleRateHz, channelCount)
            val track = audioTrack ?: return
            val written = track.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) {
                logger.w("AudioTrack write returned %d", written)
            }
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            val samples = max(1, written / (2 * max(1, channelCount)))
            val durationMs = (samples.toDouble() / sampleRateHz * 1000).toLong()
            if (durationMs > 0) {
                delay(durationMs)
            }
        } catch (t: Throwable) {
            logger.e("Failed to play Nova audio output", throwable = t)
        }
    }

    fun stop() {
        releaseTrack()
    }

    private fun ensureAudioTrack(sampleRateHz: Int, channelCount: Int) {
        val desired = PlaybackConfig(sampleRateHz, channelCount)
        if (currentConfig == desired && audioTrack != null) return
        releaseTrack()
        val channelMask = if (channelCount <= 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuffer, sampleRateHz * channelCount)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(channelMask)
            .build()
        audioTrack = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        currentConfig = desired
    }

    private fun releaseTrack() {
        audioTrack?.let { track ->
            try {
                track.stop()
            } catch (_: IllegalStateException) {
                // ignored
            } finally {
                track.release()
            }
        }
        audioTrack = null
        currentConfig = null
    }

    private data class PlaybackConfig(
        val sampleRateHz: Int,
        val channelCount: Int
    )
}
