package com.example.frontieraudio.ui.jarvis

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.frontieraudio.ui.theme.FrontierTheme
import kotlin.math.roundToInt

private val JarvisBackground = Color(0xFF0C0D0F)
private val JarvisSurface = Color(0xFF16181C)
private val JarvisAccent = Color(0xFFFF6A00)
private val JarvisInfo = Color(0xFF1F8AFF)
private val JarvisSuccess = Color(0xFF43A047)
private val JarvisWarning = Color(0xFFFFC107)
private val JarvisError = Color(0xFFFF3B30)

@Composable
fun JarvisControlPanel(
    state: JarvisUiState,
    onToggle: (Boolean) -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        color = JarvisBackground,
        border = BorderStroke(1.dp, Color(0xFF20232A)),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        0f to JarvisBackground,
                        0.5f to JarvisSurface,
                        1f to JarvisBackground
                    )
                )
                .padding(FrontierTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)
        ) {
            JarvisHeader(state)
            JarvisSessionSummary(state)
            JarvisToggleControl(state, onToggle)
            Divider(color = Color(0xFF2A2E35))
            JarvisSignalStack(state)
            Divider(color = Color(0xFF2A2E35))
            JarvisActions(state, onStopSession)
        }
    }
}

@Composable
private fun JarvisHeader(state: JarvisUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)) {
        Text(
            text = "Jarvis Real-Time Assistant",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = Color.White
        )
        val subtitle = when {
            state.statusMessage != null -> state.statusMessage
            state.isEnabled && state.linkStatus == JarvisLinkStatus.CONNECTED ->
                "Standing by for field commands."
            state.isEnabled && state.linkStatus == JarvisLinkStatus.CONNECTING ->
                "Establishing secure uplink..."
            state.isEnabled && state.linkStatus == JarvisLinkStatus.DEGRADED ->
                "Link degraded – monitoring signal integrity."
            else -> "Jarvis offline. Enable when ready for deployment."
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFAEB4BB)
        )
    }
}

@Composable
private fun JarvisSessionSummary(state: JarvisUiState) {
    Surface(
        color = Color(0xFF1B1E24),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2E35))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FrontierTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill(
                    label = state.linkStatus.label.uppercase(),
                    color = state.linkStatus.primaryColor()
                )
                Text(
                    text = state.connectionLatencyMs?.let { "Latency ${it} ms" } ?: "Latency n/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8F949C)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Session",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    ),
                    color = Color(0xFFAEB4BB)
                )
                Text(
                    text = state.sessionId.takeOrPlaceholder(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun JarvisToggleControl(
    state: JarvisUiState,
    onToggle: (Boolean) -> Unit
) {
    val toggleLabel = if (state.isEnabled) "Disable Jarvis" else "Enable Jarvis"
    Column(
        verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        JarvisPulseIndicator(
            isSpeaking = state.isSpeaking,
            isListening = state.isListening,
            accent = JarvisAccent
        )
        Button(
            onClick = { onToggle(!state.isEnabled) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isEnabled) JarvisAccent else Color(0xFF20232A),
                contentColor = if (state.isEnabled) Color.Black else Color.White
            )
        ) {
            Text(
                text = toggleLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun JarvisPulseIndicator(
    isSpeaking: Boolean,
    isListening: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp
) {
    val baseColor = if (isListening || isSpeaking) accent else JarvisInfo
    val innerColor = if (isListening || isSpeaking) Color.Black else Color(0xFF1B1E24)
    val animatedScale by animateFloatAsState(
        targetValue = if (isListening || isSpeaking) 1.05f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "listeningScale"
    )
    val circleSize = (size.value * animatedScale).dp
    val pulseSize = if (isSpeaking) {
        val transition = rememberInfiniteTransition(label = "speakingPulse")
        val pulseFactor by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        (size.value * pulseFactor).dp
    } else {
        size
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .size(pulseSize)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
            )
        }
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(innerColor)
                .borderWithGlow(color = baseColor, strokeWidth = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            val statusLabel = when {
                isSpeaking -> "Responding"
                isListening -> "Listening"
                else -> "Standby"
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                ),
                color = if (isListening || isSpeaking) baseColor else Color(0xFFAEB4BB)
            )
        }
    }
}

@Composable
private fun JarvisSignalStack(state: JarvisUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)) {
        SignalHeaderRow(isListening = state.isListening, isSpeaking = state.isSpeaking)
        JarvisVuMeter(
            level = state.vuLevel,
            isActive = state.isListening || state.isSpeaking
        )
    }
}

@Composable
private fun SignalHeaderRow(isListening: Boolean, isSpeaking: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Signal Monitor",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = when {
                    isListening -> "Mic live – capturing operator audio."
                    isSpeaking -> "Jarvis verbal response active."
                    else -> "Awaiting command input."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAEB4BB)
            )
        }
        StatusPill(
            label = when {
                isSpeaking -> "Responding"
                isListening -> "Listening"
                else -> "Idle"
            },
            color = when {
                isSpeaking -> JarvisAccent
                isListening -> JarvisInfo
                else -> Color(0xFF4C5562)
            }
        )
    }
}

@Composable
private fun JarvisVuMeter(level: Float, isActive: Boolean, modifier: Modifier = Modifier) {
    val target = level.coerceIn(0f, 1f)
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) target else target * 0.3f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "vuLevel"
    )
    val meterColor = when {
        animatedLevel > 0.75f -> JarvisAccent
        animatedLevel > 0.4f -> JarvisInfo
        else -> Color(0xFF2D3A43)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B2126))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(meterColor)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Input level",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAEB4BB)
            )
            Text(
                text = "${(animatedLevel * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = meterColor
            )
        }
    }
}

@Composable
private fun JarvisActions(state: JarvisUiState, onStopSession: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onStopSession,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, JarvisAccent),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = JarvisAccent
            ),
            enabled = state.isEnabled
        ) {
            Text(
                text = "Stop Session",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
        Spacer(modifier = Modifier.width(FrontierTheme.spacing.small))
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1B1E24),
            border = BorderStroke(1.dp, Color(0xFF2A2E35))
        ) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = interaction,
                        indication = null
                    ) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 18.dp),
                    text = "Hold to Talk",
                    color = Color(0xFF8F949C),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = color,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

private fun JarvisLinkStatus.primaryColor(): Color = when (this) {
    JarvisLinkStatus.DISCONNECTED -> JarvisError
    JarvisLinkStatus.CONNECTING -> JarvisWarning
    JarvisLinkStatus.CONNECTED -> JarvisSuccess
    JarvisLinkStatus.DEGRADED -> JarvisWarning
}

private fun Modifier.borderWithGlow(color: Color, strokeWidth: Dp): Modifier =
    this.then(
        Modifier.drawBehind {
            val stroke = strokeWidth.toPx()
            drawCircle(
                color = color.copy(alpha = 0.45f),
                style = Stroke(width = stroke)
            )
        }
    )

private fun String?.takeOrPlaceholder(): String = this ?: "Unassigned"

@Preview(showBackground = true)
@Composable
private fun JarvisControlPanelPreview() {
    MaterialTheme {
        JarvisControlPanel(
            state = JarvisUiState(
                isEnabled = true,
                linkStatus = JarvisLinkStatus.CONNECTED,
                sessionId = "session-123456789",
                connectionLatencyMs = 142,
                isListening = true,
                isSpeaking = false,
                vuLevel = 0.65f
            ),
            onToggle = {},
            onStopSession = {}
        )
    }
}


