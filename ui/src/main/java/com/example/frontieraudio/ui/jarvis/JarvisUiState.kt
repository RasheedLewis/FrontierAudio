package com.example.frontieraudio.ui.jarvis

import androidx.compose.runtime.Immutable

@Immutable
data class JarvisUiState(
    val isEnabled: Boolean = false,
    val linkStatus: JarvisLinkStatus = JarvisLinkStatus.DISCONNECTED,
    val sessionId: String? = null,
    val connectionLatencyMs: Long? = null,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val vuLevel: Float = 0f,
    val statusMessage: String? = null
)

enum class JarvisLinkStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    DEGRADED("Degraded")
}


