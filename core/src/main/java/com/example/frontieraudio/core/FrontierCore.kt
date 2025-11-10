package com.example.frontieraudio.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder entry point for shared services across modules.
 */
@Singleton
class FrontierCore @Inject constructor() {
    val initialized: Boolean
        get() = true
}

