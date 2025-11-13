package com.example.frontieraudio.jarvis.nova

import com.example.frontieraudio.core.logging.FrontierLogger
import com.example.frontieraudio.jarvis.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider

@Module
@InstallIn(SingletonComponent::class)
object NovaModule {

    @Provides
    @Singleton
    fun provideNovaConfig(): JarvisNovaConfig = JarvisNovaConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun provideAwsCredentialsProvider(): AwsCredentialsProvider {
        val accessKey = BuildConfig.NOVA_ACCESS_KEY
        val secretKey = BuildConfig.NOVA_SECRET_KEY
        val sessionToken = BuildConfig.NOVA_SESSION_TOKEN
        val hasStaticCreds = accessKey.isNotBlank() && secretKey.isNotBlank()
        if (!hasStaticCreds) {
            return DefaultCredentialsProvider.create()
        }
        val creds = if (sessionToken.isNotBlank()) {
            AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
        } else {
            AwsBasicCredentials.create(accessKey, secretKey)
        }
        return StaticCredentialsProvider.create(creds)
    }

    @Provides
    @Singleton
    fun provideNovaStreamingClient(
        config: JarvisNovaConfig,
        logger: FrontierLogger,
        credentialsProvider: AwsCredentialsProvider
    ): NovaStreamingClient = NovaStreamingClient(config, logger, credentialsProvider)
}


