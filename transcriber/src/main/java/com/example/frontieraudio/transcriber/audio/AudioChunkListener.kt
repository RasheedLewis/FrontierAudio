package com.example.frontieraudio.transcriber.audio

fun interface AudioChunkListener {
    fun onAudioChunk(chunk: ShortArray, sampleCount: Int, timestampMillis: Long)
}

