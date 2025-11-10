package com.example.frontieraudio.startup

import android.util.Log
import com.example.frontieraudio.core.FrontierCore
import com.example.frontieraudio.core.storage.datastore.FrontierPreferenceStore
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class FrontierStartupCoordinator
@Inject
constructor(
        private val frontierCore: FrontierCore,
        private val jarvisModule: JarvisModule,
        private val transcriberModule: TranscriberModule,
        private val preferenceStore: FrontierPreferenceStore
) {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        startupScope.launch {
            val firstLaunch = preferenceStore.isFirstLaunch.first()
            if (firstLaunch) {
                Log.i(TAG, "Detected first app launch. Marking as completed.")
                preferenceStore.markAppLaunched()
            }
        }
    }

    private companion object {
        private const val TAG = "FrontierStartup"
    }
}
