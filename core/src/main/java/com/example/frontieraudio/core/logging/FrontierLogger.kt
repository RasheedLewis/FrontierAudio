package com.example.frontieraudio.core.logging

import com.example.frontieraudio.core.environment.EnvironmentConfig

/**
 * Shared logging abstraction to route logs through a single entry point.
 */
interface FrontierLogger {
    fun initialize(config: EnvironmentConfig)

    fun v(message: String, vararg args: Any?)
    fun d(message: String, vararg args: Any?)
    fun i(message: String, vararg args: Any?)
    fun w(message: String, vararg args: Any?)
    fun e(message: String, vararg args: Any?, throwable: Throwable? = null)
}

