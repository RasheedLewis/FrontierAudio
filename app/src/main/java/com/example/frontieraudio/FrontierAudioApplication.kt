package com.example.frontieraudio

import android.app.Application
import com.example.frontieraudio.startup.FrontierStartupCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrontierAudioApplication : Application() {

    @Inject lateinit var startupCoordinator: FrontierStartupCoordinator

    override fun onCreate() {
        super.onCreate()
        startupCoordinator.initialize()
    }
}

