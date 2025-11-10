package com.example.frontieraudio.core.logging

import android.util.Log
import com.example.frontieraudio.core.environment.Environment
import com.example.frontieraudio.core.environment.EnvironmentConfig
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class TimberFrontierLogger @Inject constructor() : FrontierLogger {

    private var initialized = false
    private var config: EnvironmentConfig? = null

    @Synchronized
    override fun initialize(config: EnvironmentConfig) {
        if (initialized) return
        this.config = config

        val tree: Timber.Tree = when (config.environment) {
            Environment.DEVELOPMENT -> Timber.DebugTree()
            Environment.STAGING, Environment.PRODUCTION -> ReleaseLoggingTree(
                allowVerbose = config.loggingEnabled
            )
        }

        Timber.plant(tree)
        initialized = true
    }

    override fun v(message: String, vararg args: Any?) {
        if (!initialized || config?.loggingEnabled != true) return
        Timber.v(message, *args)
    }

    override fun d(message: String, vararg args: Any?) {
        if (!initialized || config?.loggingEnabled != true) return
        Timber.d(message, *args)
    }

    override fun i(message: String, vararg args: Any?) {
        if (!initialized || config?.loggingEnabled != true) return
        Timber.i(message, *args)
    }

    override fun w(message: String, vararg args: Any?) {
        if (!initialized) return
        Timber.w(message, *args)
    }

    override fun e(message: String, vararg args: Any?, throwable: Throwable?) {
        if (!initialized) return
        if (throwable != null) {
            Timber.e(throwable, message, *args)
        } else {
            Timber.e(message, *args)
        }
    }

    private class ReleaseLoggingTree(
        private val allowVerbose: Boolean
    ) : Timber.Tree() {

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!allowVerbose && priority < Log.WARN) {
                return
            }
            val targetPriority = if (allowVerbose) priority else priority.coerceAtLeast(Log.WARN)
            Log.println(targetPriority, tag ?: DEFAULT_TAG, message)
            t?.let { Log.println(targetPriority, tag ?: DEFAULT_TAG, Log.getStackTraceString(it)) }
        }

        companion object {
            private const val DEFAULT_TAG = "Frontier"
        }
    }
}

