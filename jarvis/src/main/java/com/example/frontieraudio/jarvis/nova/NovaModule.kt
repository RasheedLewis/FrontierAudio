package com.example.frontieraudio.jarvis.nova

import com.example.frontieraudio.core.logging.FrontierLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

@Module
@InstallIn(SingletonComponent::class)
object NovaModule {

    @Provides
    @Singleton
    fun provideNovaConfig(): JarvisNovaConfig = JarvisNovaConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun provideAwsCredentialsProvider(): AwsCredentialsProvider =
        DefaultCredentialsProvider.create()

    @Provides
    @Singleton
    fun provideNovaStreamingClient(
        config: JarvisNovaConfig,
        logger: FrontierLogger,
        credentialsProvider: AwsCredentialsProvider
    ): NovaStreamingClient = NovaStreamingClient(config, logger, credentialsProvider)
}


