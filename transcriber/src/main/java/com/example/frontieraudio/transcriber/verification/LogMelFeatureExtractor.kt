package com.example.frontieraudio.transcriber.verification

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class LogMelFeatureExtractor(
    private val sampleRateHz: Int,
    private val frameLength: Int,
    private val frameStep: Int,
    private val fftSize: Int,
    private val melBinCount: Int,
    private val frameCount: Int,
    private val lowerFrequencyHz: Float = 125f,
    private val upperFrequencyHz: Float = 7500f
) {

    private val spectrumSize = fftSize / 2 + 1
    private val hannWindow: FloatArray = FloatArray(frameLength) { index ->
        (0.5f - 0.5f * cos((2.0 * PI * index) / (frameLength - 1))).toFloat()
    }
    private val melFilterBank: Array<FloatArray> = createMelFilterBank()

    fun compute(samples: ShortArray, targetFrameCount: Int = frameCount): Array<FloatArray> {
        val requiredSamples = frameStep * (targetFrameCount - 1) + frameLength
        val normalized = FloatArray(requiredSamples) { index ->
            if (index < samples.size) {
                samples[index] / Short.MAX_VALUE.toFloat()
            } else {
                0f
            }
        }

        val frames = Array(targetFrameCount) { FloatArray(melBinCount) }
        val spectrum = FloatArray(spectrumSize)
        val frameBuffer = FloatArray(fftSize)
        val twoPiOverFft = (2.0 * PI / fftSize)

        for (frameIndex in 0 until targetFrameCount) {
            val offset = frameIndex * frameStep
            // Apply window and zero-pad
            for (n in 0 until frameLength) {
                frameBuffer[n] = normalized[offset + n] * hannWindow[n]
            }
            for (n in frameLength until fftSize) {
                frameBuffer[n] = 0f
            }

            // Compute power spectrum using DFT
            for (k in 0..fftSize / 2) {
                var real = 0.0
                var imag = 0.0
                for (n in 0 until fftSize) {
                    val angle = twoPiOverFft * k * n
                    val sample = frameBuffer[n].toDouble()
                    real += sample * cos(angle)
                    imag -= sample * sin(angle)
                }
                spectrum[k] = (real * real + imag * imag).toFloat()
            }

            val melEnergies = frames[frameIndex]
            for (m in 0 until melBinCount) {
                val weights = melFilterBank[m]
                var energy = 0.0
                for (k in 0 until spectrumSize) {
                    energy += weights[k] * spectrum[k]
                }
                melEnergies[m] = ln((energy + EPSILON)).toFloat()
            }
        }
        return frames
    }

    private fun createMelFilterBank(): Array<FloatArray> {
        val filters = Array(melBinCount) { FloatArray(spectrumSize) }

        val lowerMel = hzToMel(lowerFrequencyHz)
        val upperMel = hzToMel(upperFrequencyHz.coerceAtMost(sampleRateHz / 2f))
        val melRange = upperMel - lowerMel
        val melPoints = FloatArray(melBinCount + 2) { index ->
            lowerMel + (index.toFloat() / (melBinCount + 1)) * melRange
        }
        val hzPoints = FloatArray(melPoints.size) { index -> melToHz(melPoints[index]) }
        val binPoints = IntArray(hzPoints.size) { index ->
            ((fftSize + 1) * hzPoints[index] / sampleRateHz).toInt().coerceIn(0, spectrumSize - 1)
        }

        for (m in 1..melBinCount) {
            val left = binPoints[m - 1]
            val center = binPoints[m]
            val right = binPoints[m + 1]
            if (center <= left || right <= center) continue

            for (k in left until center) {
                filters[m - 1][k] = ((k - left).toFloat() / (center - left)).coerceIn(0f, 1f)
            }
            for (k in center until right) {
                filters[m - 1][k] = ((right - k).toFloat() / (right - center)).coerceIn(0f, 1f)
            }
        }

        return filters
    }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)

    private fun melToHz(mel: Float): Float {
        val power = (mel / 2595f).toDouble()
        return (700.0 * (10.0.pow(power) - 1.0)).toFloat()
    }

    companion object {
        private const val EPSILON = 1e-10f
    }

    fun supports(targetFrameCount: Int, targetMelBinCount: Int): Boolean =
        targetFrameCount == frameCount && targetMelBinCount == melBinCount
}
