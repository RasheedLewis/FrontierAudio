package com.example.frontieraudio.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.frontieraudio.core.FrontierCore
import com.example.frontieraudio.core.environment.Environment
import com.example.frontieraudio.core.environment.EnvironmentConfig
import com.example.frontieraudio.core.permissions.PermissionSnapshot
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import com.example.frontieraudio.ui.ControlCenterStatus

@Preview(showBackground = true, name = "Typography Styles")
@Composable
private fun TypographyPreview() {
    FrontierAudioTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "Frontier Control Center", style = MaterialTheme.typography.headlineLarge)
            Text(text = "Mission-ready diagnostics at a glance.", style = MaterialTheme.typography.bodySmall)
            Text(text = "Operator Status", style = MaterialTheme.typography.titleMedium)
            Text(text = "Continue", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, name = "Control Center Screen")
@Composable
private fun ControlCenterStatusPreview() {
    FrontierAudioTheme {
        ControlCenterStatus(
            jarvisModule = JarvisModule(FrontierCore()),
            transcriberModule = TranscriberModule(FrontierCore()),
            isFirstLaunch = false,
            isVoiceEnrolled = true,
            permissionSnapshot = PermissionSnapshot(
            recordAudioGranted = true,
            internetGranted = true,
            fineLocationGranted = true
        ),
            environmentConfig = EnvironmentConfig(
                environment = Environment.DEVELOPMENT,
                apiBaseUrl = "https://dev.api.frontieraudio",
                loggingEnabled = true
            ),
            onVoiceEnrollmentClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
