package com.example.frontieraudio.environment

import com.example.frontieraudio.BuildConfig
import com.example.frontieraudio.core.environment.Environment
import com.example.frontieraudio.core.environment.EnvironmentConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EnvironmentModule {

    @Provides
    @Singleton
    fun provideEnvironmentConfig(): EnvironmentConfig =
        EnvironmentConfig(
            environment = Environment.from(BuildConfig.ENVIRONMENT),
            apiBaseUrl = BuildConfig.API_BASE_URL,
            loggingEnabled = BuildConfig.ENABLE_LOGGING
        )
}

