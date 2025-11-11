package com.example.frontieraudio.transcriber.verification

import com.example.frontieraudio.transcriber.audio.AudioProcessingConfig
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class SpeakerVerificationHarnessTest {

    private val sampleRateHz = 16_000
    private val harness =
        SpeakerVerificationHarness(
            sampleRateHz = sampleRateHz,
            processingConfig =
                AudioProcessingConfig(
                    sampleRateHz = sampleRateHz,
                    targetRms = 0.12f,
                    minGain = 0.6f,
                    maxGain = 4.0f,
                    smoothingFactor = 0.75f,
                    noiseFloorDb = -55f,
                    highPassCutoffHz = 120f
                )
        )

    @Test
    fun sameSpeakerSamplesScoreWithHighConfidence() {
        val enrollmentClips =
            listOf(
                AudioFixtures.compositeVoice(
                    baseFrequencyHz = 310f,
                    harmonics = listOf(2f to 0.35f, 3f to 0.22f),
                    amplitude = 0.55f,
                    durationSeconds = 1.0f,
                    sampleRateHz = sampleRateHz
                ),
                AudioFixtures.compositeVoice(
                    baseFrequencyHz = 315f,
                    harmonics = listOf(2.5f to 0.28f, 3.5f to 0.18f),
                    amplitude = 0.42f,
                    durationSeconds = 1.0f,
                    sampleRateHz = sampleRateHz
                ),
                AudioFixtures.compositeVoice(
                    baseFrequencyHz = 305f,
                    harmonics = listOf(2f to 0.30f, 4f to 0.16f),
                    amplitude = 0.6f,
                    durationSeconds = 1.0f,
                    sampleRateHz = sampleRateHz
                )
            )
        val profile = harness.buildProfile(enrollmentClips)

        val candidate =
            AudioFixtures.compositeVoice(
                baseFrequencyHz = 308f,
                harmonics = listOf(2.2f to 0.32f, 3.2f to 0.2f),
                amplitude = 0.5f,
                durationSeconds = 1.0f,
                sampleRateHz = sampleRateHz
            )

        val result = harness.match(profile, candidate)
        assertTrue(result.confidence >= 0.6f, "Expected confidence ≥ 0.6 but was ${result.confidence}")
    }

    @Test
    fun differentSpeakersProduceLowConfidence() {
        val speakerAClips =
            listOf(
                AudioFixtures.compositeVoice(
                    baseFrequencyHz = 260f,
                    harmonics = listOf(2f to 0.28f, 3f to 0.16f),
                    amplitude = 0.55f,
                    durationSeconds = 1.0f,
                    sampleRateHz = sampleRateHz
                ),
                AudioFixtures.compositeVoice(
                    baseFrequencyHz = 255f,
                    harmonics = listOf(2.5f to 0.27f, 3.5f to 0.19f),
                    amplitude = 0.48f,
                    durationSeconds = 1.0f,
                    sampleRateHz = sampleRateHz
                ),
            )
        val profile = harness.buildProfile(speakerAClips)

        val speakerB =
            AudioFixtures.compositeVoice(
                baseFrequencyHz = 470f,
                harmonics = listOf(2f to 0.4f, 3f to 0.25f),
                amplitude = 0.52f,
                durationSeconds = 1.0f,
                sampleRateHz = sampleRateHz
            )

        val result = harness.match(profile, speakerB)
        assertTrue(result.confidence <= 0.35f, "Expected low confidence but was ${result.confidence}")
    }

    @Test
    fun gainNormalizationBringsRmsIntoTargetBand() {
        val quietClip =
            AudioFixtures.sineWave(
                frequencyHz = 320f,
                amplitude = 0.08f,
                durationSeconds = 1.0f,
                sampleRateHz = sampleRateHz
            )
        val loudClip =
            AudioFixtures.sineWave(
                frequencyHz = 320f,
                amplitude = 0.9f,
                durationSeconds = 1.0f,
                sampleRateHz = sampleRateHz
            )

        val quietAnalysis = harness.analyzeClip(quietClip)
        val loudAnalysis = harness.analyzeClip(loudClip)

        val rawDiff = abs(quietAnalysis.rawRms - loudAnalysis.rawRms)
        val processedDiff = abs(quietAnalysis.processedRms - loudAnalysis.processedRms)

        assertTrue(quietAnalysis.processedRms > quietAnalysis.rawRms, "Quiet clip should gain volume")
        assertTrue(loudAnalysis.processedRms < loudAnalysis.rawRms, "Loud clip should be attenuated")
        assertTrue(
            processedDiff < rawDiff * 0.8f,
            "Processed diff $processedDiff should be meaningfully less than raw diff $rawDiff"
        )
        assertTrue(
            quietAnalysis.processedRms in 0.04f..0.6f,
            "Quiet clip RMS ${quietAnalysis.processedRms} should be within a normalized band"
        )
        assertTrue(
            loudAnalysis.processedRms in 0.04f..0.6f,
            "Loud clip RMS ${loudAnalysis.processedRms} should be within a normalized band"
        )
    }

    @Test
    fun truncatedClipFallsBackToZeroConfidence() {
        val profile =
            harness.buildProfile(
                listOf(
                    AudioFixtures.sineWave(
                        frequencyHz = 300f,
                        amplitude = 0.5f,
                        durationSeconds = 1.0f,
                        sampleRateHz = sampleRateHz
                    )
                )
            )

        val truncated = ShortArray(1) { 0 }
        val result = harness.match(profile, truncated)
        assertEquals(0f, result.confidence, "Expected zero confidence for empty-like clip")
    }

}


