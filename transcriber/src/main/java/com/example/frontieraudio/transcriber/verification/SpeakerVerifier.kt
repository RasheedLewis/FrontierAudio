package com.example.frontieraudio.transcriber.verification

import kotlinx.coroutines.flow.StateFlow

interface SpeakerVerifier {
    val state: StateFlow<SpeakerVerificationState>

    fun acceptWindow(data: ByteArray, timestampMillis: Long)

    fun reset()
}

data class SpeakerVerificationState(
    val status: VerificationStatus,
    val confidence: Float,
    val timestampMillis: Long
) {
    companion object {
        val UNKNOWN = SpeakerVerificationState(VerificationStatus.UNKNOWN, 0f, 0L)
    }
}

enum class VerificationStatus {
    MATCH,
    MISMATCH,
    UNKNOWN
}

data class SpeakerVerificationConfig(
    val modelAssetPath: String = "models/speaker_verification.tflite",
    val matchThreshold: Float = 0.6f
)

