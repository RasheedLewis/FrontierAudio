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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        ControlCenterStatus(
                                jarvisModule = jarvisModule,
                                transcriberModule = transcriberModule,
                                isFirstLaunch = isFirstLaunch,
                                isVoiceEnrolled = isVoiceEnrolled,
                                permissionSnapshot = permissionSnapshotState.value,
                                environmentConfig = environmentConfig,
                                onVoiceEnrollmentClick = { manualEnrollment = true },
                                onRequestPermissions = requestPermissions,
                                modifier = Modifier.padding(innerPadding)
                        )
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
