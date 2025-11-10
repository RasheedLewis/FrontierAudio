package com.example.frontieraudio

import android.app.Application
import com.example.frontieraudio.core.environment.EnvironmentConfig
import com.example.frontieraudio.core.logging.FrontierLogger
import com.example.frontieraudio.startup.FrontierStartupCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrontierAudioApplication : Application() {

    @Inject lateinit var startupCoordinator: FrontierStartupCoordinator
    @Inject lateinit var environmentConfig: EnvironmentConfig
    @Inject lateinit var logger: FrontierLogger

    override fun onCreate() {
        super.onCreate()
        logger.initialize(environmentConfig)
        logger.i(
            "Frontier environment: %s | Base URL: %s | Logging enabled: %s",
            environmentConfig.environment,
            environmentConfig.apiBaseUrl,
            environmentConfig.loggingEnabled
        )
        startupCoordinator.initialize()
    }
}

