package com.example.frontieraudio.transcriber.audio

import android.media.AudioFormat
import android.media.AudioRecord
import kotlin.math.max

data class AudioCaptureConfig(
    val sampleRateInHz: Int = DEFAULT_SAMPLE_RATE,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val bufferSizeInBytes: Int = calculateSuggestedBufferSize(
        sampleRateInHz = sampleRateInHz,
        channelConfig = channelConfig,
        audioFormat = audioFormat
    )
) {

    val bufferSizeInShorts: Int
        get() = bufferSizeInBytes / SHORT_BYTES

    companion object {
        private const val DEFAULT_SAMPLE_RATE = 16_000
        private const val MIN_BUFFER_MULTIPLIER = 2
        private const val SHORT_BYTES = 2

        private fun calculateSuggestedBufferSize(
            sampleRateInHz: Int,
            channelConfig: Int,
            audioFormat: Int
        ): Int {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
            val frameSize = when (audioFormat) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_IEC61937 -> 2
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }

            val channelCount = when (channelConfig) {
                AudioFormat.CHANNEL_IN_MONO -> 1
                AudioFormat.CHANNEL_IN_STEREO -> 2
                else -> 1
            }

            val preferredSize = sampleRateInHz / 10 * frameSize * channelCount
            return max(minBufferSize, preferredSize) * MIN_BUFFER_MULTIPLIER
        }
    }
}

