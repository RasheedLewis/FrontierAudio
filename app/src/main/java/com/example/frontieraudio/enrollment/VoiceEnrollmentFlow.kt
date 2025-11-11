package com.example.frontieraudio.enrollment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.frontieraudio.ui.theme.FrontierTheme
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class EnrollmentStep {
    Entry,
    Recording,
    Processing,
    Success,
    Failure
}

data class RecordedSampleInfo(
    val durationSeconds: Float,
    val rms: Float
)

@Composable
fun VoiceEnrollmentFlow(
    controller: VoiceEnrollmentController,
    hasMicPermission: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    userName: String = "Operator",
    onDismiss: () -> Unit = {}
) {
    val prompts = remember(userName) {
        listOf(
            "Frontier, start recording now.",
            "Safety first, always on time.",
            "My name is ${userName.trim()}."
        )
    }
    var step by remember { mutableStateOf(EnrollmentStep.Entry) }
    var promptIndex by remember { mutableStateOf(0) }
    val recordedSamples = remember { mutableMapOf<Int, ByteArray>() }
    val recordedSampleInfo = remember { mutableMapOf<Int, RecordedSampleInfo>() }
    var isRecording by remember { mutableStateOf(false) }
    var recordingError by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val currentAmplitude by controller.currentAmplitude.collectAsState(initial = 0f)

    DisposableEffect(Unit) {
        onDispose { controller.cancelRecording() }
    }

    LaunchedEffect(step) {
        if (step == EnrollmentStep.Processing) {
            recordingError = null
            infoMessage = null
            val orderedSamples = prompts.indices.map { recordedSamples[it] ?: ByteArray(0) }
            val success = controller.finalizeEnrollment(orderedSamples)
            delay(1_000)
            step = if (success) EnrollmentStep.Success else EnrollmentStep.Failure
        } else if (step == EnrollmentStep.Recording) {
            infoMessage = null
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FrontierTheme.spacing.section),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                shadowElevation = 24.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(FrontierTheme.spacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)
                ) {
                    when (step) {
                        EnrollmentStep.Entry -> EnrollmentIntro(
                            onBegin = { step = EnrollmentStep.Recording },
                            onExplain = {
                                infoMessage =
                                    "Frontier Audio uses on-device AI to recognize your voice and prevent unauthorized recording."
                            }
                        )

                        EnrollmentStep.Recording -> EnrollmentPrompt(
                            index = promptIndex,
                            total = prompts.size,
                            prompt = prompts[promptIndex],
                            hasMicPermission = hasMicPermission,
                            isRecording = isRecording,
                            hasRecording = recordedSamples.containsKey(promptIndex),
                            sampleInfo = recordedSampleInfo[promptIndex],
                            amplitude = currentAmplitude,
                            errorMessage = recordingError,
                            onRecord = {
                                if (!hasMicPermission) {
                                    recordingError = "Microphone permission required before recording."
                                    onRequestPermissions()
                                    return@EnrollmentPrompt
                                }
                                recordingError = null
                                val started = controller.startRecording()
                                if (!started) {
                                    recordingError = "Unable to access microphone. Please try again."
                                } else {
                                    isRecording = true
                                }
                            },
                            onStop = {
                                val bytes = controller.stopRecording()
                                isRecording = false
                                if (bytes.isEmpty()) {
                                    recordingError = "We couldn’t hear anything. Try again."
                                } else {
                                    recordedSamples[promptIndex] = bytes
                                    val durationSeconds = controller.estimateDurationSeconds(bytes)
                                    val rms = controller.estimateRms(bytes)
                                    recordedSampleInfo[promptIndex] = RecordedSampleInfo(durationSeconds, rms)
                                }
                            },
                            onContinue = {
                                recordingError = null
                                if (promptIndex + 1 >= prompts.size) {
                                    step = EnrollmentStep.Processing
                                } else {
                                    promptIndex += 1
                                }
                            },
                            onRerecord = {
                                controller.cancelRecording()
                                isRecording = false
                                recordedSamples.remove(promptIndex)
                                recordedSampleInfo.remove(promptIndex)
                            },
                            onRequestPermissions = onRequestPermissions
                        )

                        EnrollmentStep.Processing -> ProcessingView()

                        EnrollmentStep.Success -> ResultView(
                            title = "✅ Voice Enrolled Successfully",
                            message = "Frontier Audio will now recognize you automatically.",
                            primaryLabel = "Continue to App",
                            onPrimary = {
                                controller.cancelRecording()
                                onDismiss()
                            }
                        )

                        EnrollmentStep.Failure -> ResultView(
                            title = "⚠️ We couldn’t get a clear sample",
                            message = "Try again in a quiet environment.",
                            primaryLabel = "Retry Enrollment",
                            onPrimary = {
                                controller.cancelRecording()
                                recordedSamples.clear()
                                recordedSampleInfo.clear()
                                promptIndex = 0
                                step = EnrollmentStep.Recording
                            }
                        )
                    }

                    if (recordingError != null && step != EnrollmentStep.Success) {
                        Text(
                            text = recordingError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (infoMessage != null) {
                        Text(
                            text = infoMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (step == EnrollmentStep.Entry) {
                        Text(
                            text = "For your safety and privacy, Frontier Audio only listens to you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollmentIntro(
    onBegin: () -> Unit,
    onExplain: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)) {
        Text(
            text = "🎙️ Voice Enrollment Required",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "For your safety and privacy, Frontier Audio only listens to you. Record a short sample to verify your voice.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onBegin,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Begin Enrollment")
        }
        OutlinedButton(
            onClick = onExplain,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("What’s this?")
        }
    }
}

@Composable
private fun EnrollmentPrompt(
    index: Int,
    total: Int,
    prompt: String,
    hasMicPermission: Boolean,
    isRecording: Boolean,
    hasRecording: Boolean,
    sampleInfo: RecordedSampleInfo?,
    amplitude: Float,
    errorMessage: String?,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onRerecord: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enrollment • Step ${index + 1} of $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${index + 1}/$total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.medium)) {
            val quotedPrompt = "\"$prompt\""
            Text(
                text = quotedPrompt,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Speak clearly, about 6 inches from your mic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VoiceWaveform(isActive = isRecording, amplitude = amplitude)

        if (!hasMicPermission) {
            Text(
                text = "Microphone permission is required before recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Grant Permission")
            }
        }

        if (sampleInfo != null && hasRecording) {
            SampleSummary(info = sampleInfo)
        }

        Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.small)) {
            when {
                isRecording -> {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Stop Recording")
                    }
                }
                hasRecording -> {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(if (index + 1 == total) "Analyze Recording" else "Continue")
                    }
                    OutlinedButton(
                        onClick = onRerecord,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Re-record")
                    }
                }
                else -> {
                    Button(
                        onClick = onRecord,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        enabled = hasMicPermission
                    ) {
                        Text("Record Sample")
                    }
                }
            }
        }

        if (errorMessage != null && !isRecording) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SampleSummary(info: RecordedSampleInfo) {
    val durationSeconds = info.durationSeconds
    val rmsPercent = (info.rms * 100f).coerceIn(0f, 100f)
    val durationText = if (durationSeconds < 0.05f) "<0.1s" else "${"%.1f".format(durationSeconds)}s"
    val qualityText = when {
        info.rms < 0.05f -> "Signal very low"
        info.rms < 0.12f -> "Signal modest"
        else -> "Signal strong"
    }
    Column(verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.tiny)) {
        Text(
            text = "Captured $durationText • RMS ${(rmsPercent.roundToInt())}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = qualityText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ProcessingView() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large)
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = "Analyzing your voice signature…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultView(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FrontierTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(primaryLabel)
        }
    }
}

@Composable
private fun VoiceWaveform(isActive: Boolean, amplitude: Float) {
    val smoothedAmplitude by animateFloatAsState(
        targetValue = if (isActive) amplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "waveAmplitude"
    )
    val transition = rememberInfiniteTransition(label = "wavePhase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhaseValue"
    )
    val baseHeights = remember { listOf(12f, 22f, 34f, 18f, 30f, 16f, 26f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        baseHeights.forEachIndexed { index, base ->
            val oscillation = ((sin(phase + index * 0.65f) + 1f) / 2f)
            val dynamicBoost = 28f * smoothedAmplitude * (0.6f + 0.4f * oscillation)
            val height = (base + dynamicBoost).dp
            val colorAlpha = if (isActive) 0.25f + (0.6f * smoothedAmplitude * (0.5f + 0.5f * oscillation)) else 0.25f
            val color = MaterialTheme.colorScheme.secondary.copy(alpha = colorAlpha.coerceIn(0.25f, 0.95f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
