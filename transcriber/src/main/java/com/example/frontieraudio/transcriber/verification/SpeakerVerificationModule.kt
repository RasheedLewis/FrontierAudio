package com.example.frontieraudio.transcriber.verification

import android.content.Context
import com.example.frontieraudio.core.logging.FrontierLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeakerVerificationModule {

    @Provides
    @Singleton
    fun provideSpeakerVerificationConfig(): SpeakerVerificationConfig = SpeakerVerificationConfig(
        matchThreshold = 0.3f
    )

    @Provides
    @Singleton
    fun provideSpeakerVerifier(
        @ApplicationContext context: Context,
        logger: FrontierLogger,
        config: SpeakerVerificationConfig
    ): SpeakerVerifier = TFLiteSpeakerVerifier(context, logger, config)
}

