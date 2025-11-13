package com.example.frontieraudio.jarvis.nova

import java.util.UUID

data class NovaInferenceConfiguration(
    val maxTokens: Int = 1024,
    val temperature: Double = 0.7,
    val topP: Double = 0.9
)

data class NovaSessionRequest(
    val sessionId: String = UUID.randomUUID().toString(),
    val systemPrompt: String,
    val inferenceConfiguration: NovaInferenceConfiguration = NovaInferenceConfiguration(),
    val metadata: Map<String, Any?> = emptyMap()
)

interface NovaStreamListener {
    fun onSessionEstablished(requestId: String?)
    fun onEventPayload(eventJson: String)
    fun onError(throwable: Throwable)
    fun onSessionClosed()
}


