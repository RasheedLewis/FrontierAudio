package com.example.frontieraudio

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.frontieraudio.core.environment.EnvironmentConfig
import com.example.frontieraudio.core.permissions.PermissionManager
import com.example.frontieraudio.core.permissions.PermissionSnapshot
import com.example.frontieraudio.core.storage.datastore.FrontierPreferenceStore
import com.example.frontieraudio.enrollment.VoiceEnrollmentController
import com.example.frontieraudio.enrollment.VoiceEnrollmentFlow
import com.example.frontieraudio.enrollment.VoiceProfileRepository
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import com.example.frontieraudio.transcriber.audio.AudioCaptureService
import com.example.frontieraudio.ui.ControlCenterStatus
import com.example.frontieraudio.ui.theme.FrontierAudioTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var jarvisModule: JarvisModule
    @Inject lateinit var transcriberModule: TranscriberModule
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var preferenceStore: FrontierPreferenceStore
    @Inject lateinit var environmentConfig: EnvironmentConfig
    @Inject lateinit var voiceProfileRepository: VoiceProfileRepository

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var permissionStateHolder: androidx.compose.runtime.MutableState<PermissionSnapshot>? =
            null

    private var audioCaptureService: AudioCaptureService? = null
    private var isServiceBound: Boolean = false

    private val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as? AudioCaptureService.AudioCaptureBinder ?: return
                    audioCaptureService = binder.service
                    isServiceBound = true
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    audioCaptureService = null
                    isServiceBound = false
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        result ->
                    permissionManager.handlePermissionResult(result)
                    permissionStateHolder?.value = permissionManager.snapshot()
                    if (!permissionManager.shouldRequestCriticalPermissions()) {
                        startAndBindAudioService()
                    }
                }

        if (permissionManager.shouldRequestCriticalPermissions()) {
            val permissions = permissionManager.missingCriticalPermissions().toTypedArray()
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions)
            }
        } else {
            startAndBindAudioService()
        }

        setContent {
            FrontierAudioTheme {
                val permissionSnapshotState = remember {
                    mutableStateOf(permissionManager.snapshot())
                }
                val isFirstLaunch by preferenceStore.isFirstLaunch.collectAsState(initial = true)
                val isVoiceEnrolled by
                        preferenceStore.isVoiceEnrolled.collectAsState(initial = false)
                var manualEnrollment by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(isVoiceEnrolled) {
                    if (isVoiceEnrolled) {
                        manualEnrollment = false
                    }
                }

                val requestPermissions: () -> Unit = {
                    val missing = permissionManager.missingCriticalPermissions().toTypedArray()
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing)
                    }
                }

                LaunchedEffect(Unit) { permissionStateHolder = permissionSnapshotState }
                DisposableEffect(Unit) {
                    onDispose {
                        if (permissionStateHolder === permissionSnapshotState) {
                            permissionStateHolder = null
                        }
                    }
                }

                val enrollmentController =
                        remember(voiceProfileRepository, preferenceStore) {
                            VoiceEnrollmentController(
                                    serviceProvider = { audioCaptureService },
                                    voiceProfileRepository = voiceProfileRepository,
                                    preferenceStore = preferenceStore
                            )
                        }

                val streamingStateFlow = remember { transcriberModule.streamingEnabledFlow() }
                val isStreamingEnabled by
                        streamingStateFlow.collectAsState(initial = transcriberModule.isListening())
                val isStreamingSupported = remember { transcriberModule.isStreamingSupported() }
                val transcriptLines = remember { mutableStateListOf<TranscriptEntry>() }
                var partialTranscript by remember { mutableStateOf<String?>(null) }
                var transcriptCounter by remember { mutableStateOf(0L) }

                LaunchedEffect(isStreamingEnabled) {
                    if (isStreamingEnabled) {
                        transcriptLines.clear()
                        partialTranscript = null
                        transcriberModule.transcripts().collect { transcript ->
                            val text = transcript.text.trim()
                            if (text.isEmpty()) return@collect
                            if (transcript.isPartial) {
                                partialTranscript = text
                            } else {
                                partialTranscript = null
                                transcriptCounter += 1
                                transcriptLines.add(0, TranscriptEntry(id = transcriptCounter, text = text))
                                if (transcriptLines.size > MAX_TRANSCRIPT_HISTORY) {
                                    transcriptLines.removeLastOrNull()
                                }
                            }
                        }
                    } else {
                        partialTranscript = null
                    }
                }

                val showEnrollment = !isVoiceEnrolled || manualEnrollment

                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground
                    ) { innerPadding ->
                        val scrollState = rememberScrollState()
                        Column(
                                modifier =
                                        Modifier.padding(innerPadding)
                                                .fillMaxSize()
                                                .verticalScroll(scrollState)
                                                .padding(horizontal = 24.dp, vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ControlCenterStatus(
                                    jarvisModule = jarvisModule,
                                    isTranscriberStreaming = isStreamingEnabled,
                                    isTranscriberSupported = isStreamingSupported,
                                    isFirstLaunch = isFirstLaunch,
                                    isVoiceEnrolled = isVoiceEnrolled,
                                    permissionSnapshot = permissionSnapshotState.value,
                                    environmentConfig = environmentConfig,
                                    onVoiceEnrollmentClick = { manualEnrollment = true },
                                    onRequestPermissions = requestPermissions,
                                    modifier = Modifier.fillMaxWidth()
                            )

                            StreamingControlCard(
                                    isStreamingSupported = isStreamingSupported,
                                    isStreamingEnabled = isStreamingEnabled,
                                    onToggleStreaming = { enabled ->
                                        if (isStreamingSupported) {
                                            if (!permissionSnapshotState.value.recordAudioGranted) {
                                                requestPermissions()
                                                return@StreamingControlCard
                                            }
                                            val captureService = audioCaptureService
                                            if (enabled) {
                                                captureService?.startCapture(streamToVerifier = true)
                                            } else {
                                                captureService?.stopCapture()
                                            }
                                            transcriberModule.setStreamingEnabled(enabled)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                            )

                            StreamingTranscriptFeed(
                                    isStreamingSupported = isStreamingSupported,
                                    isStreamingEnabled = isStreamingEnabled,
                                    partialTranscript = partialTranscript,
                                    transcripts = transcriptLines,
                                    modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (showEnrollment) {
                        VoiceEnrollmentFlow(
                                controller = enrollmentController,
                                hasMicPermission = permissionSnapshotState.value.recordAudioGranted,
                                onRequestPermissions = requestPermissions,
                                modifier = Modifier.fillMaxSize().align(Alignment.Center),
                                userName = "Operator",
                                onDismiss = { manualEnrollment = false }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!permissionManager.shouldRequestCriticalPermissions()) {
            startAndBindAudioService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun startAndBindAudioService() {
        val intent = Intent(this, AudioCaptureService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }
}

@Composable
private fun StreamingControlCard(
    isStreamingSupported: Boolean,
    isStreamingEnabled: Boolean,
    onToggleStreaming: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Transcribe Streaming",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val statusText =
                    when {
                        !isStreamingSupported -> "Unavailable in this environment."
                        isStreamingEnabled -> "Streaming session active."
                        else -> "Streaming is paused."
                    }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val checked = isStreamingSupported && isStreamingEnabled
            Switch(
                checked = checked,
                onCheckedChange = { enabled ->
                    if (isStreamingSupported) {
                        onToggleStreaming(enabled)
                    }
                },
                enabled = isStreamingSupported
            )
        }
    }
}

@Composable
private fun StreamingTranscriptFeed(
    isStreamingSupported: Boolean,
    isStreamingEnabled: Boolean,
    partialTranscript: String?,
    transcripts: List<TranscriptEntry>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Live Transcript Feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Newest entries appear first while streaming is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                !isStreamingSupported -> {
                    Text(
                        text = "Transcribe streaming is disabled for this environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !isStreamingEnabled -> {
                    Text(
                        text = "Turn on streaming to begin receiving live transcripts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                transcripts.isEmpty() && partialTranscript == null -> {
                    Text(
                        text = "Listening for speech…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        partialTranscript?.let { partial ->
                            item(key = "partial") {
                                TranscriptLine(text = partial, isPartial = true)
                            }
                        }
                        items(transcripts, key = { it.id }) { entry ->
                            TranscriptLine(text = entry.text, isPartial = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptLine(text: String, isPartial: Boolean) {
    val baseStyle = if (isPartial) {
        MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (isPartial) "… $text" else "• $text",
            style = baseStyle,
            color = if (isPartial) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (isPartial) {
            Text(
                text = "processing…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

private data class TranscriptEntry(val id: Long, val text: String)

private const val MAX_TRANSCRIPT_HISTORY = 40
