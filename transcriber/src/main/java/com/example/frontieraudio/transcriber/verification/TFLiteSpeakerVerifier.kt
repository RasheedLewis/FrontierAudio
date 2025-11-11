package com.example.frontieraudio.transcriber.verification

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import com.example.frontieraudio.core.logging.FrontierLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter

@Singleton
class TFLiteSpeakerVerifier @Inject constructor(
    @ApplicationContext context: Context,
    private val logger: FrontierLogger,
    private val config: SpeakerVerificationConfig
) : SpeakerVerifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SpeakerVerificationState.UNKNOWN)
    override val state: StateFlow<SpeakerVerificationState> = _state

    private val interpreter: Interpreter? = loadInterpreter(context.assets)
    private val inputShape: IntArray? = interpreter?.getInputTensor(0)?.shape()
    private val outputShape: IntArray? = interpreter?.getOutputTensor(0)?.shape()

    override fun acceptWindow(data: ByteArray, timestampMillis: Long) {
        if (data.isEmpty()) return
        scope.launch {
            processWindow(data, timestampMillis)
        }
    }

    override fun reset() {
        _state.value = SpeakerVerificationState.UNKNOWN
    }

    private suspend fun processWindow(data: ByteArray, timestampMillis: Long) {
        interpreter ?: return updateFallbackState(data, timestampMillis)

        val localInputShape = inputShape
        val localOutputShape = outputShape
        if (localInputShape == null || localOutputShape == null) {
            logger.w("SpeakerVerifier input/output shapes unavailable; using fallback.")
            return updateFallbackState(data, timestampMillis)
        }

        if (localInputShape.any { it <= 0 } || localOutputShape.any { it <= 0 }) {
            logger.w(
                "SpeakerVerifier tensor shapes unresolved (${localInputShape.contentToString()} -> ${localOutputShape.contentToString()}); using fallback."
            )
            return updateFallbackState(data, timestampMillis)
        }

        val samples = byteArrayToShortArray(data)

        val expectedSampleCount =
            localInputShape.takeIf { it.isNotEmpty() }?.last()?.takeIf { it > 0 } ?: samples.size
        val normalizedSamples = FloatArray(expectedSampleCount) { index ->
            val sample = if (index < samples.size) samples[index] else 0
            sample / Short.MAX_VALUE.toFloat()
        }
        val inputTensor = arrayOf(normalizedSamples)

        val outputVectorLength =
            localOutputShape.takeIf { it.isNotEmpty() }?.last()?.coerceAtLeast(1) ?: 1
        val outputTensor = Array(1) { FloatArray(outputVectorLength) }

        try {
            interpreter.run(inputTensor, outputTensor)
            val confidence = outputTensor.firstOrNull()?.firstOrNull()?.coerceIn(0f, 1f) ?: 0f
            val status = if (confidence >= config.matchThreshold) {
                VerificationStatus.MATCH
            } else {
                VerificationStatus.MISMATCH
            }
            _state.value = SpeakerVerificationState(status, confidence, timestampMillis)
        } catch (throwable: Throwable) {
            logger.e("Speaker verification inference failed", throwable = throwable)
            updateFallbackState(data, timestampMillis)
        }
    }

    private fun updateFallbackState(data: ByteArray, timestampMillis: Long) {
        val samples = byteArrayToShortArray(data)
        val rms = computeRms(samples)
        val confidence = min(1f, rms / FALLBACK_RMS_DENOMINATOR)
        _state.value = SpeakerVerificationState(VerificationStatus.UNKNOWN, confidence, timestampMillis)
    }

    private fun loadInterpreter(assetManager: AssetManager): Interpreter? {
        return try {
            val model = loadModelFile(assetManager, config.modelAssetPath)
            if (model != null) {
                Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
            } else {
                logger.w("Speaker verification model not found at ${config.modelAssetPath}; running in fallback mode.")
                null
            }
        } catch (io: IOException) {
            logger.e("Unable to load speaker verification model", throwable = io)
            null
        }
    }

    private fun loadModelFile(assets: AssetManager, assetPath: String): MappedByteBuffer? =
        try {
            val descriptor: AssetFileDescriptor = assets.openFd(assetPath)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        } catch (io: IOException) {
            null
        }

    private fun byteArrayToShortArray(data: ByteArray): ShortArray {
        val shortCount = data.size / Short.SIZE_BYTES
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val result = ShortArray(shortCount)
        buffer.asShortBuffer().get(result)
        return result
    }

    private fun computeRms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in samples) {
            val normalized = sample / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        val mean = sumSquares / samples.size
        return sqrt(mean).toFloat()
    }

    fun shutdown() {
        scope.cancel()
        interpreter?.close()
    }

    companion object {
        private const val FALLBACK_RMS_DENOMINATOR = 0.4f
    }
}

