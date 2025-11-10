package com.example.frontieraudio.transcriber

import com.example.frontieraudio.core.FrontierCore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary placeholder representing the Transcriber feature module.
 */
@Singleton
class TranscriberModule @Inject constructor(
    private val frontierCore: FrontierCore
) {
    fun isListening(): Boolean = frontierCore.initialized
}

