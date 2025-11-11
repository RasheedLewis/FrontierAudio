package com.example.frontieraudio.transcriber.cloud

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

/**
 * Supplies AWS credentials for Transcribe streaming requests.
 *
 * Projects should provide an implementation that retrieves signed credentials from a secure source
 * (e.g. Cognito, STS, custom token broker). The default implementation throws to signal that
 * configuration is required.
 */
fun interface TranscribeCredentialsProvider {
    suspend fun get(): AwsCredentialsProvider
}

