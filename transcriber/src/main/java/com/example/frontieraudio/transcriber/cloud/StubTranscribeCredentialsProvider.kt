package com.example.frontieraudio.transcriber.cloud

import com.example.frontieraudio.transcriber.BuildConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultChainTranscribeCredentialsProvider @Inject constructor() :
    TranscribeCredentialsProvider {
    override suspend fun get(): AwsCredentialsProvider {
        val accessKey = BuildConfig.TRANSCRIBE_ACCESS_KEY
        val secretKey = BuildConfig.TRANSCRIBE_SECRET_KEY
        val sessionToken = BuildConfig.TRANSCRIBE_SESSION_TOKEN

        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            val credentials = if (sessionToken.isNotBlank()) {
                AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
            } else {
                AwsBasicCredentials.create(accessKey, secretKey)
            }

            return StaticCredentialsProvider.create(credentials)
        }

        return DefaultCredentialsProvider.create()
    }
}

