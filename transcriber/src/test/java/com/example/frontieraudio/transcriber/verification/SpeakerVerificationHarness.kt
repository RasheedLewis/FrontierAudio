package com.example.frontieraudio.transcriber.verification

import com.example.frontieraudio.transcriber.audio.AudioProcessingConfig
import com.example.frontieraudio.transcriber.audio.NoiseFilteringProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class SpeakerVerificationHarness(
    private val sampleRateHz: Int,
    private val processingConfig: AudioProcessingConfig =
        AudioProcessingConfig(sampleRateHz = sampleRateHz)
) {

    val targetRms: Float = processingConfig.targetRms

    private val analysisFrequenciesHz: FloatArray =
        FloatArray(16) { index -> 160f + index * 60f }

    fun analyzeClip(raw: ShortArray): ClipAnalysis {
        require(raw.isNotEmpty()) { "A clip must contain at least one sample." }
        val cloned = raw.copyOf()
        val preRms = computeRms(cloned)
        val processed = processSamples(cloned)
        val postRms = computeRms(processed)
        val spectrum = computeSpectrum(processed)
        return ClipAnalysis(
            rawRms = preRms,
            processedRms = postRms,
            spectrum = spectrum,
            processedSamples = processed
        )
    }

    fun buildProfile(clips: List<ShortArray>): SpeakerProfile {
        require(clips.isNotEmpty()) { "At least one clip required to build a profile." }
        val analyses = clips.map { analyzeClip(it) }
        val dimension = analyses.first().spectrum.size
        val meanSpectrum = FloatArray(dimension)
        var rmsSum = 0f
        for (analysis in analyses) {
            for (i in 0 until dimension) {
                meanSpectrum[i] += analysis.spectrum[i]
            }
            rmsSum += analysis.processedRms
        }
        if (analyses.isNotEmpty()) {
            for (i in 0 until dimension) {
                meanSpectrum[i] /= analyses.size
            }
        }
        val normalizedSpectrum = normalizeDistribution(meanSpectrum)
        val meanRms = rmsSum / analyses.size
        return SpeakerProfile(spectrum = normalizedSpectrum, meanRms = meanRms)
    }

    fun match(profile: SpeakerProfile, candidate: ShortArray): SpeakerMatchResult {
        val analysis = analyzeClip(candidate)
        val hasSignal =
            analysis.processedRms > 1e-4f || analysis.spectrum.any { it > 1e-6f }
        if (!hasSignal) {
            return SpeakerMatchResult(
                confidence = 0f,
                spectralScore = 0f,
                rmsScore = 0f,
                processedRms = analysis.processedRms,
                candidateSpectrum = analysis.spectrum
            )
        }

        val spectralDistance = l1Distance(profile.spectrum, analysis.spectrum)
        val spectralScore = (1f - 0.5f * spectralDistance).coerceIn(0f, 1f)
        val rmsScore =
            if (profile.meanRms <= 1e-4f) 1f
            else {
                val deviation = abs(profile.meanRms - analysis.processedRms) / profile.meanRms
                (1f - deviation).coerceIn(0f, 1f)
            }
        val confidence = (spectralScore * 0.85f + rmsScore * 0.15f).coerceIn(0f, 1f)
        return SpeakerMatchResult(
            confidence = confidence,
            spectralScore = spectralScore,
            rmsScore = rmsScore,
            processedRms = analysis.processedRms,
            candidateSpectrum = analysis.spectrum
        )
    }

    private fun processSamples(samples: ShortArray): ShortArray {
        val processor = NoiseFilteringProcessor(processingConfig)
        return processor.process(samples, samples.size)
    }

    private fun computeSpectrum(samples: ShortArray): FloatArray {
        val normalized = FloatArray(samples.size) { index ->
            samples[index] / Short.MAX_VALUE.toFloat()
        }
        val magnitudes = FloatArray(analysisFrequenciesHz.size)
        for (index in analysisFrequenciesHz.indices) {
            val frequency = analysisFrequenciesHz[index]
            var sinSum = 0.0
            var cosSum = 0.0
            for (n in normalized.indices) {
                val angle = 2.0 * PI * frequency * n / sampleRateHz
                val sample = normalized[n]
                sinSum += sample * sin(angle)
                cosSum += sample * cos(angle)
            }
            val magnitude = sqrt(sinSum.pow(2) + cosSum.pow(2)) / normalized.size
            magnitudes[index] = magnitude.toFloat()
        }
        val smoothed = smoothSpectrum(magnitudes)
        return normalizeDistribution(smoothed)
    }

    private fun smoothSpectrum(vector: FloatArray): FloatArray {
        if (vector.isEmpty()) return vector
        val result = FloatArray(vector.size)
        for (i in vector.indices) {
            val center = vector[i]
            val prev = if (i == 0) center else vector[i - 1]
            val next = if (i == vector.lastIndex) center else vector[i + 1]
            result[i] = (center * 0.6f) + (prev * 0.2f) + (next * 0.2f)
        }
        return result
    }

    private fun computeRms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in samples) {
            val normalized = sample / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    private fun computeRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    private fun l1Distance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must be the same length." }
        var sum = 0.0
        for (i in a.indices) {
            sum += abs(a[i] - b[i])
        }
        return sum.toFloat()
    }

    private fun normalizeDistribution(vector: FloatArray): FloatArray {
        val sum = vector.sum()
        if (sum <= 1e-8f) {
            return FloatArray(vector.size)
        }
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = (vector[i] / sum).coerceAtLeast(0f)
        }
        return normalized
    }
}

internal data class ClipAnalysis(
    val rawRms: Float,
    val processedRms: Float,
    val spectrum: FloatArray,
    val processedSamples: ShortArray
)

internal data class SpeakerProfile(
    val spectrum: FloatArray,
    val meanRms: Float
)

internal data class SpeakerMatchResult(
    val confidence: Float,
    val spectralScore: Float,
    val rmsScore: Float,
    val processedRms: Float,
    val candidateSpectrum: FloatArray
)

internal object AudioFixtures {
    fun sineWave(
        frequencyHz: Float,
        amplitude: Float,
        durationSeconds: Float,
        sampleRateHz: Int
    ): ShortArray {
        val totalSamples = (durationSeconds * sampleRateHz).roundToInt().coerceAtLeast(1)
        val clampedAmplitude = amplitude.coerceIn(0f, 1f)
        val result = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val angle = 2.0 * PI * frequencyHz * i / sampleRateHz
            val value = sin(angle).toFloat() * clampedAmplitude
            val scaled = (value * Short.MAX_VALUE).toInt()
            result[i] = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    fun compositeVoice(
        baseFrequencyHz: Float,
        harmonics: List<Pair<Float, Float>>,
        amplitude: Float,
        durationSeconds: Float,
        sampleRateHz: Int
    ): ShortArray {
        val totalSamples = (durationSeconds * sampleRateHz).roundToInt().coerceAtLeast(1)
        val buffer = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            var sampleValue = 0f
            val baseAngle = 2f * PI.toFloat() * baseFrequencyHz * i / sampleRateHz
            sampleValue += sin(baseAngle) * amplitude
            for ((multiple, weight) in harmonics) {
                val angle = 2f * PI.toFloat() * baseFrequencyHz * multiple * i / sampleRateHz
                sampleValue += sin(angle) * amplitude * weight
            }
            buffer[i] = sampleValue
        }
        val max = buffer.maxOf { abs(it) }.takeIf { it > 1e-6f } ?: 1f
        val shorts = ShortArray(totalSamples)
        for (i in buffer.indices) {
            val normalized = (buffer[i] / max).coerceIn(-1f, 1f)
            shorts[i] = (normalized * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return shorts
    }
}


