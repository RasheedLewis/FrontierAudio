package com.example.frontieraudio.jarvis

import com.example.frontieraudio.core.FrontierCore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary placeholder representing the Jarvis feature module.
 */
@Singleton
class JarvisModule @Inject constructor(
    private val frontierCore: FrontierCore
) {
    fun isEnabled(): Boolean = frontierCore.initialized
}

