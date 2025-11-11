package com.example.frontieraudio.transcriber

import com.example.frontieraudio.core.FrontierCore
import com.example.frontieraudio.transcriber.cloud.TranscribeStreamingConfig
import com.example.frontieraudio.transcriber.cloud.TranscribeStreamingCoordinator
import com.example.frontieraudio.transcriber.cloud.TranscribeStreamingService
import com.example.frontieraudio.transcriber.cloud.TranscribeTranscript
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Temporary placeholder representing the Transcriber feature module.
 */
@Singleton
class TranscriberModule @Inject constructor(
    private val frontierCore: FrontierCore,
    private val streamingCoordinator: TranscribeStreamingCoordinator,
    private val streamingConfig: TranscribeStreamingConfig,
    private val streamingService: TranscribeStreamingService
) {
    fun isListening(): Boolean =
        streamingConfig.enabled && streamingCoordinator.currentStreamingEnabled()

    fun streamingEnabledFlow(): StateFlow<Boolean> = streamingCoordinator.streamingEnabled()

    fun transcripts(): Flow<TranscribeTranscript> = streamingService.transcripts()

    fun setStreamingEnabled(enabled: Boolean) {
        if (!streamingConfig.enabled) return
        streamingCoordinator.setStreamingEnabled(enabled)
    }

    fun isStreamingSupported(): Boolean = streamingConfig.enabled && frontierCore.initialized
}

