package com.example.frontieraudio.transcriber.audio

import kotlin.math.abs
import kotlin.math.sqrt

class NoiseFilteringProcessor(
    private val config: AudioProcessingConfig
) {

    private var previousInput = 0f
    private var previousOutput = 0f
    private var previousGain = 1f

    fun process(samples: ShortArray, size: Int): ShortArray {
        if (size == 0) return samples

        val floats = FloatArray(size)
        var sumSquares = 0.0

        for (i in 0 until size) {
            val normalized = samples[i] / MAX_SHORT_VALUE
            floats[i] = normalized
            sumSquares += normalized * normalized
        }

        val rms = sqrt(sumSquares / size).toFloat()
        val targetGain = if (rms <= 1e-6f) config.maxGain
        else (config.targetRms / rms).coerceIn(config.minGain, config.maxGain)

        val gain = (config.smoothingFactor * previousGain) +
            ((1f - config.smoothingFactor) * targetGain)
        previousGain = gain

        var prevInputLocal = previousInput
        var prevOutputLocal = previousOutput
        val alpha = config.highPassAlpha
        val noiseGate = config.noiseFloorAmplitude

        for (i in 0 until size) {
            val input = floats[i]
            val highPassed = alpha * (prevOutputLocal + input - prevInputLocal)
            prevInputLocal = input
            prevOutputLocal = highPassed

            var processed = highPassed
            if (abs(processed) < noiseGate) {
                processed = 0f
            } else {
                processed *= gain
            }

            processed = processed.coerceIn(-1f, 1f)
            floats[i] = processed
            samples[i] = (processed * Short.MAX_VALUE).toInt().coerceIn(MIN_SHORT, MAX_SHORT_INT).toShort()
        }

        previousInput = prevInputLocal
        previousOutput = prevOutputLocal

        return samples
    }

    companion object {
        private const val MIN_SHORT = -32768
        private const val MAX_SHORT_INT = 32767
        private const val MAX_SHORT_VALUE = 32768f
    }
}

