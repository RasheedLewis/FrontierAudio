package com.example.frontieraudio.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.frontieraudio.core.permissions.PermissionSnapshot
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule

/**
 * Simple status surface to prove cross-module wiring.
 */
@Composable
fun ControlCenterStatus(
    jarvisModule: JarvisModule,
    transcriberModule: TranscriberModule,
    isFirstLaunch: Boolean,
    permissionSnapshot: PermissionSnapshot,
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

    Text(
        text = "Frontier Control Center: $systemStatus | $permissionsStatus | $firstLaunchStatus",
        modifier = modifier
    )
}

