package com.example.frontieraudio.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule

/**
 * Simple status surface to prove cross-module wiring.
 */
@Composable
fun ControlCenterStatus(
    jarvisModule: JarvisModule,
    transcriberModule: TranscriberModule,
    modifier: Modifier = Modifier
) {
    val jarvisReady = jarvisModule.isEnabled()
    val transcriberReady = transcriberModule.isListening()
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

