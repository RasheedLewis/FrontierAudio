package com.example.frontieraudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    permissionSnapshot: PermissionSnapshot,
    environmentConfig: EnvironmentConfig,
    modifier: Modifier = Modifier
) {
    val jarvisReady = jarvisModule.isEnabled()
    val transcriberReady = transcriberModule.isListening()
    val permissionsReady = permissionSnapshot.allCriticalGranted

    val environmentLabel = environmentConfig.environment.name
        .lowercase(Locale.US)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
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

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)) {
                Text(
                    text = "Environment",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                EnvironmentRow(
                    environmentLabel = environmentLabel,
                    config = environmentConfig
                )
                Text(
                    text = if (isFirstLaunch) "First-time deployment: enrollment recommended."
                    else "Returning operator: settings synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    highlight: Color,
    badgeLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    config: EnvironmentConfig
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
            color = if (config.loggingEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
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
            modifier = Modifier
                .padding(
                    horizontal = FrontierTheme.spacing.small,
                    vertical = FrontierTheme.spacing.tiny
                ),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
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
