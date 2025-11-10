package com.example.frontieraudio.transcriber.audio

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Aggregates audio short-array chunks into fixed byte windows for downstream streaming.
 */
class AudioStreamingPipeline(
    windowSizeBytes: Int,
    private val onWindowAvailable: (ByteArray, Long) -> Unit
) {

    private val listeners = CopyOnWriteArraySet<AudioWindowListener>()
    private val window = AudioBufferWindow(windowSizeBytes) { bytes, timestamp ->
        listeners.forEach { it.onWindow(bytes, timestamp) }
        onWindowAvailable(bytes, timestamp)
    }

    fun registerWindowListener(listener: AudioWindowListener) {
        listeners += listener
    }

    fun unregisterWindowListener(listener: AudioWindowListener) {
        listeners -= listener
    }

    fun appendChunk(chunk: ShortArray, sampleCount: Int, timestampMillis: Long) {
        window.append(chunk, sampleCount, timestampMillis)
    }

    fun flush() {
        window.flush()
    }
}

fun interface AudioWindowListener {
    fun onWindow(data: ByteArray, timestampMillis: Long)
}

