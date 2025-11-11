package com.example.frontieraudio.transcriber.cloud

import com.example.frontieraudio.core.logging.FrontierLogger
import com.example.frontieraudio.transcriber.BuildConfig
import com.example.frontieraudio.transcriber.verification.SpeakerVerifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

@Module
@InstallIn(SingletonComponent::class)
object TranscribeStreamingModule {

    @Provides
    @Singleton
    @TranscribeScope
    fun provideTranscribeScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideTranscribeCredentialsProvider(
        impl: DefaultChainTranscribeCredentialsProvider
    ): TranscribeCredentialsProvider = impl

    @Provides
    @Singleton
    fun provideTranscribeConfig(
        credentialsProvider: TranscribeCredentialsProvider
    ): TranscribeStreamingConfig =
        TranscribeStreamingConfig(
            region = BuildConfig.TRANSCRIBE_REGION,
            sampleRateHz = 16_000,
            credentialsProviderFactory = { credentialsProvider.get() },
            enabled = BuildConfig.TRANSCRIBE_ENABLED
        )

    @Provides
    @Singleton
    fun provideTranscribeStreamingService(
        config: TranscribeStreamingConfig,
        logger: FrontierLogger,
        @TranscribeScope scope: CoroutineScope
    ): TranscribeStreamingService =
        TranscribeStreamingService(
            config = config,
            logger = logger,
            scope = scope
        )

    @Provides
    @Singleton
    fun provideTranscribeStreamingCoordinator(
        service: TranscribeStreamingService,
        speakerVerifier: SpeakerVerifier,
        logger: FrontierLogger,
        @TranscribeScope scope: CoroutineScope
    ): TranscribeStreamingCoordinator =
        TranscribeStreamingCoordinator(
            service = service,
            verificationState = speakerVerifier.state,
            logger = logger,
            scope = scope
        )
}

