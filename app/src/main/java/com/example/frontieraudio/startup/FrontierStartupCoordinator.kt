package com.example.frontieraudio.startup

import android.util.Log
import com.example.frontieraudio.core.FrontierCore
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrontierStartupCoordinator
@Inject
constructor(
        private val frontierCore: FrontierCore,
        private val jarvisModule: JarvisModule,
        private val transcriberModule: TranscriberModule
) {

    fun initialize() {
        val coreReady = frontierCore.initialized
        val jarvisReady = jarvisModule.isEnabled()
        val transcriberReady = transcriberModule.isListening()

        Log.i(TAG, "Frontier Core initialized: $coreReady")
        Log.i(TAG, "Jarvis module ready: $jarvisReady")
        Log.i(TAG, "Transcriber module ready: $transcriberReady")

        if (!coreReady || !jarvisReady || !transcriberReady) {
            Log.w(TAG, "Startup completed with modules pending review.")
        } else {
            Log.i(TAG, "All Frontier modules initialized successfully.")
        }
    }

    private companion object {
        private const val TAG = "FrontierStartup"
    }
}
