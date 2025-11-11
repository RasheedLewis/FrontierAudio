package com.example.frontieraudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.frontieraudio.core.environment.EnvironmentConfig
import com.example.frontieraudio.core.permissions.PermissionManager
import com.example.frontieraudio.core.permissions.PermissionSnapshot
import com.example.frontieraudio.core.storage.datastore.FrontierPreferenceStore
import com.example.frontieraudio.jarvis.JarvisModule
import com.example.frontieraudio.transcriber.TranscriberModule
import com.example.frontieraudio.ui.theme.FrontierAudioTheme
import com.example.frontieraudio.ui.ControlCenterStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var jarvisModule: JarvisModule
    @Inject lateinit var transcriberModule: TranscriberModule
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var preferenceStore: FrontierPreferenceStore
    @Inject lateinit var environmentConfig: EnvironmentConfig

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var permissionStateHolder: androidx.compose.runtime.MutableState<PermissionSnapshot>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            permissionManager.handlePermissionResult(result)
            permissionStateHolder?.value = permissionManager.snapshot()
        }

        if (permissionManager.shouldRequestCriticalPermissions()) {
            val permissions = permissionManager.missingCriticalPermissions().toTypedArray()
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions)
            }
        }

        setContent {
            FrontierAudioTheme {
                val permissionSnapshotState = remember {
                    mutableStateOf(permissionManager.snapshot())
                }
                val isFirstLaunch by preferenceStore.isFirstLaunch.collectAsState(initial = true)
                LaunchedEffect(Unit) {
                    permissionStateHolder = permissionSnapshotState
                }
                DisposableEffect(Unit) {
                    onDispose {
                        if (permissionStateHolder === permissionSnapshotState) {
                            permissionStateHolder = null
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) { innerPadding ->
                    ControlCenterStatus(
                        jarvisModule = jarvisModule,
                        transcriberModule = transcriberModule,
                        isFirstLaunch = isFirstLaunch,
                        permissionSnapshot = permissionSnapshotState.value,
                        environmentConfig = environmentConfig,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}