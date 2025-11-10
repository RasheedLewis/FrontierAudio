package com.example.frontieraudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontierAudioTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlCenterStatus(
                        jarvisModule = jarvisModule,
                        transcriberModule = transcriberModule,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}