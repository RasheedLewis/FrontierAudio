package com.example.frontieraudio.startup

import com.example.frontieraudio.core.FrontierCore
import com.example.frontieraudio.core.logging.FrontierLogger
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
        private val preferenceStore: FrontierPreferenceStore,
        private val logger: FrontierLogger
) {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize() {
        val coreReady = frontierCore.initialized
        val jarvisReady = jarvisModule.isEnabled()
        val transcriberReady = transcriberModule.isListening()

        logger.i("Frontier Core initialized: %s", coreReady)
        logger.i("Jarvis module ready: %s", jarvisReady)
        logger.i("Transcriber module ready: %s", transcriberReady)

        if (!coreReady || !jarvisReady || !transcriberReady) {
            logger.w("Startup completed with modules pending review.")
        } else {
            logger.i("All Frontier modules initialized successfully.")
        }

        startupScope.launch {
            val firstLaunch = preferenceStore.isFirstLaunch.first()
            if (firstLaunch) {
                logger.i("Detected first app launch. Marking as completed.")
                preferenceStore.markAppLaunched()
            }
        }
    }
}
