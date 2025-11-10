package com.example.frontieraudio.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.frontieraudio.core.environment.EnvironmentConfig
import com.example.frontieraudio.core.permissions.PermissionSnapshot
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import java.util.Locale

/**
 * Simple status surface to prove cross-module wiring.
 */
@Composable
fun ControlCenterStatus(
    jarvisModule: JarvisModule,
    transcriberModule: TranscriberModule,
    isFirstLaunch: Boolean,
    permissionSnapshot: PermissionSnapshot,
    environmentConfig: EnvironmentConfig,
    modifier: Modifier = Modifier
) {
    val jarvisReady = jarvisModule.isEnabled()
    val transcriberReady = transcriberModule.isListening()
    val systemStatus = if (jarvisReady && transcriberReady) {
        "Core Ready"
    } else {
        "Modules Pending"
    }
    val permissionsStatus = if (permissionSnapshot.allCriticalGranted) {
        "Permissions OK"
    } else {
        val missing = permissionSnapshot.missingPermissions.joinToString()
        "Missing: $missing"
    }
    val firstLaunchStatus = if (isFirstLaunch) {
        "First Launch"
    } else {
        "Returning User"
    }
    val environmentLabel = environmentConfig.environment.name
        .lowercase(Locale.US)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    val environmentStatus = buildString {
        append(environmentLabel)
        append(" | ")
        append(environmentConfig.apiBaseUrl)
        append(" | Logging: ")
        append(if (environmentConfig.loggingEnabled) "On" else "Off")
    }

    Text(
        text = "Frontier Control Center: $systemStatus | $permissionsStatus | $firstLaunchStatus | $environmentStatus",
        modifier = modifier
    )
}

