package com.example.frontieraudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule

/**
 * Simple status surface to prove cross-module wiring.
 */
@Composable
fun ControlCenterStatus(modifier: Modifier = Modifier) {
    val jarvisReady = JarvisModule().isEnabled()
    val transcriberReady = TranscriberModule().isListening()
    val status = if (jarvisReady && transcriberReady) {
        "Core Ready"
    } else {
        "Modules Pending"
    }

    Text(
        text = "Frontier Control Center: $status",
        modifier = modifier
    )
}

