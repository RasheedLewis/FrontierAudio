package com.example.frontieraudio.jarvis.nova

import com.example.frontieraudio.jarvis.BuildConfig
import software.amazon.awssdk.regions.Region

data class JarvisNovaConfig(
    val modelId: String,
    val region: Region,
    val heartbeatIntervalMs: Long,
    val sessionTimeoutMs: Long
) {
    companion object {
        fun fromBuildConfig(): JarvisNovaConfig =
            JarvisNovaConfig(
                modelId = BuildConfig.NOVA_MODEL_ID,
                region = Region.of(BuildConfig.NOVA_REGION),
                heartbeatIntervalMs = BuildConfig.NOVA_HEARTBEAT_INTERVAL_MS,
                sessionTimeoutMs = BuildConfig.NOVA_SESSION_TIMEOUT_MS
            )
    }
}


