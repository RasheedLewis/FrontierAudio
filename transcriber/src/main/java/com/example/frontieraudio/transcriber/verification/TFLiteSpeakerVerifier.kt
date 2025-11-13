package com.example.frontieraudio.transcriber.verification

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import com.example.frontieraudio.core.logging.FrontierLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile
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
    @ApplicationContext private val context: Context,
    private val logger: FrontierLogger,
    private val config: SpeakerVerificationConfig
) : SpeakerVerifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SpeakerVerificationState.UNKNOWN)
    override val state: StateFlow<SpeakerVerificationState> = _state

    private val interpreter: Interpreter? = loadInterpreter(context.assets)
    private val inputShape: IntArray? = interpreter?.getInputTensor(0)?.shape()
    private val outputShape: IntArray? = interpreter?.getOutputTensor(0)?.shape()
    private val interpreterLock = Any()
    @Volatile private var referenceEmbedding: FloatArray? = null

    override fun acceptWindow(data: ByteArray, timestampMillis: Long) {
        if (data.isEmpty()) return
        scope.launch {
            processWindow(data, timestampMillis)
        }
    }

    override fun reset() {
        _state.value = SpeakerVerificationState.UNKNOWN
        referenceEmbedding = null
    }

    private suspend fun processWindow(data: ByteArray, timestampMillis: Long) {
        val localInterpreter = interpreter ?: return updateFallbackState(data, timestampMillis)

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

        val outputElementCount = localInterpreter.getOutputTensor(0)?.numElements() ?: 0
        if (outputElementCount <= 0) {
            logger.w("SpeakerVerifier output tensor has zero elements; using fallback.")
            return updateFallbackState(data, timestampMillis)
        }

        val samples = ensureSampleArray(byteArrayToShortArray(data), FRAME_LENGTH_PCM)
        val pcmAsFloats = normalizeSamples1d(samples)

        resizeInterpreterInput(localInterpreter, FRAME_LENGTH_PCM)

        val reference = referenceEmbedding ?: generateReferenceEmbedding(FRILL_INPUT_SHAPE, outputElementCount)
        referenceEmbedding = reference

        val outputTensor = Array(1) { FloatArray(outputElementCount) }

        try {
            synchronized(interpreterLock) {
                localInterpreter.run(arrayOf(pcmAsFloats), outputTensor)
            }
            val embedding = outputTensor.firstOrNull() ?: FloatArray(outputElementCount)
            val (status, confidence) = if (reference != null) {
                val similarity = cosineSimilarity(embedding, reference)
                val normalized = similarity.coerceIn(0f, 1f)
                val matchStatus = if (normalized >= config.matchThreshold) {
                    VerificationStatus.MATCH
                } else {
                    VerificationStatus.MISMATCH
                }
                matchStatus to normalized
            } else {
                val confidence = embedding.firstOrNull()?.coerceIn(0f, 1f) ?: 0f
                val matchStatus = if (confidence >= config.matchThreshold) {
                    VerificationStatus.MATCH
                } else {
                    VerificationStatus.MISMATCH
                }
                matchStatus to confidence
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

    private fun normalizeSamples1d(samples: ShortArray): FloatArray = FloatArray(samples.size) { index ->
        samples[index] / Short.MAX_VALUE.toFloat()
    }

    private fun ensureSampleArray(source: ShortArray, requiredLength: Int): ShortArray {
        if (source.size == requiredLength) return source
        val result = ShortArray(requiredLength)
        val copyLength = min(source.size, requiredLength)
        source.copyInto(result, 0, 0, copyLength)
        return result
    }

    private fun resizeInterpreterInput(localInterpreter: Interpreter, length: Int) {
        synchronized(interpreterLock) {
            localInterpreter.resizeInput(0, intArrayOf(1, length))
            localInterpreter.allocateTensors()
        }
    }

    private fun generateReferenceEmbedding(inputShape: IntArray, outputElementCount: Int): FloatArray? {
        val localInterpreter = interpreter ?: return null
        val profileDir = File(context.filesDir, VOICE_PROFILE_DIR)
        if (!profileDir.exists()) {
            logger.d("No enrolled voice profile found; running without speaker gating profile.")
            return null
        }

        val clipFiles = profileDir.listFiles { file ->
            file.isFile && file.extension.equals("pcm", ignoreCase = true)
        }?.sortedBy { it.name } ?: emptyList()

        if (clipFiles.isEmpty()) {
            logger.d("Voice profile directory present but empty; skipping reference embedding generation.")
            return null
        }

        val accumulator = FloatArray(outputElementCount)
        var embeddingsCount = 0
        val requiredSamples = FRAME_LENGTH_PCM
        resizeInterpreterInput(localInterpreter, requiredSamples)
        clipFiles.forEach { file ->
            runCatching {
                val bytes = file.readBytes()
                val samples = byteArrayToShortArray(bytes)
                var offset = 0
                while (offset < samples.size) {
                    val windowSamples = ShortArray(requiredSamples) { index ->
                        val sampleIndex = offset + index
                        if (sampleIndex < samples.size) samples[sampleIndex] else 0
                    }
                    val normalized = normalizeSamples1d(windowSamples)
                    val outputTensor = Array(1) { FloatArray(outputElementCount) }
                    synchronized(interpreterLock) {
                        localInterpreter.run(arrayOf(normalized), outputTensor)
                    }
                    val embedding = outputTensor[0]
                    for (i in embedding.indices) {
                        accumulator[i] += embedding[i]
                    }
                    embeddingsCount += 1
                    offset += requiredSamples
                }
            }.onFailure { throwable ->
                logger.e("Failed to generate reference embedding from ${file.name}", throwable = throwable)
            }
        }

        if (embeddingsCount == 0) {
            logger.w("Unable to derive speaker reference embedding; proceeding without gating profile.")
            return null
        }

        for (index in accumulator.indices) {
            accumulator[index] /= embeddingsCount
        }
        logger.i("Loaded speaker profile with %d embeddings", embeddingsCount)
        return accumulator
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

    private fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        if (first.isEmpty() || second.isEmpty() || first.size != second.size) return 0f
        var dot = 0f
        var normFirst = 0f
        var normSecond = 0f
        for (index in first.indices) {
            val a = first[index]
            val b = second[index]
            dot += a * b
            normFirst += a * a
            normSecond += b * b
        }
        if (normFirst == 0f || normSecond == 0f) return 0f
        return (dot / (sqrt(normFirst) * sqrt(normSecond))).coerceIn(-1f, 1f)
    }

    fun shutdown() {
        scope.cancel()
        interpreter?.close()
    }

    companion object {
        private const val FALLBACK_RMS_DENOMINATOR = 0.4f
        private const val VOICE_PROFILE_DIR = "voice_profile"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_LENGTH = 400 // 25 ms at 16 kHz
        private const val FRAME_STEP = 160   // 10 ms hop
        private const val FFT_SIZE = 512
        private const val FRAME_LENGTH_PCM = 16_000
        private val FRILL_INPUT_SHAPE = intArrayOf(1, FRAME_LENGTH_PCM)
    }
}

