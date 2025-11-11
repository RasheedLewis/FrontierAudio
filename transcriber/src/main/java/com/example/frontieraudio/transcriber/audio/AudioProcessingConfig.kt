package com.example.frontieraudio.transcriber.audio

import kotlin.math.PI
import kotlin.math.pow

data class AudioProcessingConfig(
    val sampleRateHz: Int,
    val targetRms: Float = 0.12f,
    val minGain: Float = 0.6f,
    val maxGain: Float = 4.0f,
    val noiseFloorDb: Float = -55f,
    val smoothingFactor: Float = 0.85f,
    val highPassCutoffHz: Float = 120f
) {
    init {
        require(sampleRateHz > 0) { "Sample rate must be positive." }
        require(targetRms in 0f..1f) { "Target RMS must be between 0 and 1." }
        require(minGain > 0f) { "Minimum gain must be positive." }
        require(maxGain >= minGain) { "Max gain must be greater or equal to min gain." }
        require(smoothingFactor in 0f..0.999f) { "Smoothing factor must be between 0 and 1 (exclusive)." }
        require(highPassCutoffHz in 20f..(sampleRateHz / 2f)) { "High pass cutoff must be within valid range." }
    }

    val noiseFloorAmplitude: Float = 10f.pow(noiseFloorDb / 20f)

    val highPassAlpha: Float = run {
        val twoPi = 2f * PI.toFloat()
        val rc = 1f / (twoPi * highPassCutoffHz)
        val dt = 1f / sampleRateHz.toFloat()
        (rc / (rc + dt)).coerceIn(0f, 0.995f)
    }
}

