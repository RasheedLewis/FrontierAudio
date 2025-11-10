package com.example.frontieraudio.core.environment

/**
 * Represents the current execution environment of the Frontier Audio application.
 */
enum class Environment(val configValue: String) {
    DEVELOPMENT("DEVELOPMENT"),
    STAGING("STAGING"),
    PRODUCTION("PRODUCTION");

    companion object {
        fun from(rawValue: String): Environment =
            values().firstOrNull { it.configValue.equals(rawValue, ignoreCase = true) }
                ?: DEVELOPMENT
    }
}

