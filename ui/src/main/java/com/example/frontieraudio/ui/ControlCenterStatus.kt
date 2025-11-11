package com.example.frontieraudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.frontieraudio.core.environment.EnvironmentConfig
import com.example.frontieraudio.core.permissions.PermissionSnapshot
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import com.example.frontieraudio.ui.theme.FrontierTheme
import java.util.Locale

@Composable
fun ControlCenterStatus(
        jarvisModule: JarvisModule,
        transcriberModule: TranscriberModule,
        isFirstLaunch: Boolean,
        isVoiceEnrolled: Boolean,
        permissionSnapshot: PermissionSnapshot,
        environmentConfig: EnvironmentConfig,
        onVoiceEnrollmentClick: () -> Unit,
        onRequestPermissions: () -> Unit,
        modifier: Modifier = Modifier
) {
    val jarvisReady = jarvisModule.isEnabled()
    val transcriberReady = transcriberModule.isListening()
    val permissionsReady = permissionSnapshot.allCriticalGranted

    val environmentLabel =
            environmentConfig.environment.name.lowercase(Locale.US).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }

    Surface(
            modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
    ) {
        Column(
                modifier = Modifier.padding(FrontierTheme.spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)) {
                Text(
                        text = "Frontier Control Center",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = "Mission-ready diagnostics at a glance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatusRow(
                    label = "Jarvis Module",
                    value = if (jarvisReady) "Operational" else "Offline",
                    highlight = statusColor(jarvisReady),
                    badgeLabel = if (jarvisReady) "Ready" else "Attention"
            )
            StatusRow(
                    label = "Transcriber Module",
                    value = if (transcriberReady) "Listening" else "Idle",
                    highlight = statusColor(transcriberReady),
                    badgeLabel = if (transcriberReady) "Ready" else "Attention"
            )
            StatusRow(
                    label = "Permissions",
                    value = if (permissionsReady) "All Clear" else "Action Required",
                    highlight = statusColor(permissionsReady),
                    badgeLabel = if (permissionsReady) "Ready" else "Attention"
            )

            if (!permissionsReady) {
                PermissionWarningCard(
                        missingPermissions = permissionSnapshot.missingPermissions,
                        onRequestPermissions = onRequestPermissions
                )
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)) {
                Text(
                        text = "Environment",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                EnvironmentRow(
                        environmentLabel = environmentLabel,
                        config = environmentConfig,
                        isVoiceEnrolled = isVoiceEnrolled,
                        onVoiceEnrollmentClick = onVoiceEnrollmentClick
                )
                Text(
                        text =
                                if (isFirstLaunch) "First-time deployment: enrollment recommended."
                                else "Returning operator: settings synced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, highlight: Color, badgeLabel: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.tiny)
        ) {
            Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
            )
        }
        StatusPill(color = highlight, label = badgeLabel)
    }
}

@Composable
private fun EnvironmentRow(
        environmentLabel: String,
        config: EnvironmentConfig,
        isVoiceEnrolled: Boolean,
        onVoiceEnrollmentClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.tiny)) {
        Text(
                text = "$environmentLabel mode",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
        )
        Text(
                text = config.apiBaseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
                text = "Logging ${if (config.loggingEnabled) "enabled" else "disabled"}",
                style = MaterialTheme.typography.bodySmall,
                color =
                        if (config.loggingEnabled) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
        )
        Text(
                text = if (isVoiceEnrolled) "Voice profile verified" else "Voice profile pending",
                style = MaterialTheme.typography.bodySmall,
                color =
                        if (isVoiceEnrolled) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onVoiceEnrollmentClick) {
            Text(
                    text = "Re-enroll Voice",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun StatusPill(color: Color, label: String) {
    Surface(
            color = color.copy(alpha = 0.18f),
            contentColor = color,
            shape = MaterialTheme.shapes.small
    ) {
        Text(
                text = label.uppercase(Locale.US),
                modifier =
                        Modifier.padding(
                                horizontal = FrontierTheme.spacing.small,
                                vertical = FrontierTheme.spacing.tiny
                        ),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun PermissionWarningCard(
        missingPermissions: List<String>,
        onRequestPermissions: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium
    ) {
        Column(
                modifier = Modifier.padding(FrontierTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)
        ) {
            Text(
                    text = "Microphone access required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
            )
            val reasons =
                    missingPermissions.joinToString(separator = "\n") { manifestName ->
                        when (manifestName) {
                            android.Manifest.permission.RECORD_AUDIO ->
                                    "• Record Audio – enables voice capture"
                            android.Manifest.permission.ACCESS_FINE_LOCATION ->
                                    "• Fine Location – tags transcripts for safety reports"
                            android.Manifest.permission.INTERNET ->
                                    "• Internet – syncs transcripts to HQ"
                            else -> "• ${manifestName.substringAfterLast('.')}"
                        }
                    }
            if (reasons.isNotBlank()) {
                Text(text = reasons, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onRequestPermissions) { Text("Grant Permissions") }
        }
    }
}

@Composable
private fun statusColor(isReady: Boolean): Color {
    return if (isReady) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }
}
