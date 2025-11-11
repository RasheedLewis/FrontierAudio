package com.example.frontieraudio.transcriber.cloud

/**
 * Represents a transcript segment returned from Amazon Transcribe Streaming.
 */
data class TranscribeTranscript(
    val sessionId: String,
    val text: String,
    val isPartial: Boolean,
    val startTimeSeconds: Double?,
    val endTimeSeconds: Double?,
    val resultId: String?,
    val channelId: String?,
    val speakerId: String?,
    val items: List<TranscriptItem>,
    val sequenceNumber: Long,
    val rawEventTimestampMillis: Long
) {
    data class TranscriptItem(
        val content: String,
        val startTimeSeconds: Double?,
        val endTimeSeconds: Double?,
        val type: ItemType
    )

    enum class ItemType {
        Pronunciation,
        Punctuation,
        Unknown
    }
}

