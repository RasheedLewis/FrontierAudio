package com.example.frontieraudio.transcriber.cloud

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for Amazon Transcribe Streaming integration.
 */
data class TranscribeStreamingConfig(
    val region: String,
    val languageCode: String = "en-US",
    val sampleRateHz: Int,
    val mediaEncoding: StreamingMediaEncoding = StreamingMediaEncoding.PCM,
    val sessionTimeout: Duration = 120.seconds,
    val credentialsProviderFactory: suspend () -> AwsCredentialsProvider,
    val appClient: String? = null,
    val vocabularyName: String? = null,
    val enabled: Boolean = true
)

enum class StreamingMediaEncoding {
    PCM
}

