package com.example.frontieraudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.frontieraudio.ui.theme.FrontierAudioTheme
import com.example.frontieraudio.ui.ControlCenterStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontierAudioTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlCenterStatus(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}