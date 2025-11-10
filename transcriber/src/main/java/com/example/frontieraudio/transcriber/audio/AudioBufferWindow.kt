package com.example.frontieraudio.transcriber.audio

import java.nio.ByteBuffer
import kotlin.math.min

class AudioBufferWindow(
    private val windowSizeBytes: Int,
    private val onWindowReady: (ByteArray, Long) -> Unit
) {

    private val byteBuffer: ByteBuffer = ByteBuffer.allocate(windowSizeBytes)
    private var windowStartTimestamp: Long = 0L

    val remainingCapacity: Int
        get() = byteBuffer.remaining()

    fun append(chunk: ShortArray, sampleCount: Int, timestampMillis: Long) {
        if (!byteBuffer.hasRemaining()) {
            flush()
        }

        if (byteBuffer.position() == 0) {
            windowStartTimestamp = timestampMillis
        }

        val bytesToWrite = sampleCount * Short.SIZE_BYTES
        var offset = 0
        while (offset < bytesToWrite) {
            val writable = min(byteBuffer.remaining(), bytesToWrite - offset)
            writeChunk(chunk, offset / Short.SIZE_BYTES, writable / Short.SIZE_BYTES)
            offset += writable
            if (!byteBuffer.hasRemaining()) {
                flush()
                if (offset < bytesToWrite) {
                    windowStartTimestamp = timestampMillis
                }
            }
        }
    }

    fun flush() {
        if (byteBuffer.position() == 0) return
        byteBuffer.flip()
        val data = ByteArray(byteBuffer.limit())
        byteBuffer.get(data)
        byteBuffer.clear()
        onWindowReady.invoke(data, windowStartTimestamp)
    }

    private fun writeChunk(src: ShortArray, startIndex: Int, count: Int) {
        for (i in 0 until count) {
            val value = src[startIndex + i].toInt()
            byteBuffer.put((value and 0xFF).toByte())
            byteBuffer.put(((value shr 8) and 0xFF).toByte())
        }
    }
}

