package com.example.frontieraudio.enrollment

import com.example.frontieraudio.core.storage.datastore.FrontierPreferenceStore
import com.example.frontieraudio.transcriber.audio.AudioCaptureService
import com.example.frontieraudio.transcriber.audio.AudioChunkListener
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceEnrollmentController(
    private val serviceProvider: () -> AudioCaptureService?,
    private val voiceProfileRepository: VoiceProfileRepository,
    private val preferenceStore: FrontierPreferenceStore
) {

    private val isRecording = AtomicBoolean(false)
    private val chunkBuffer = mutableListOf<ByteArray>()
    private var activeListener: AudioChunkListener? = null
    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    fun startRecording(): Boolean {
        if (isRecording.get()) return false
        val service = serviceProvider() ?: return false
        cancelRecording()
        chunkBuffer.clear()
        _currentAmplitude.value = 0f
        val listener = object : AudioChunkListener {
            override fun onAudioChunk(chunk: ShortArray, size: Int, timestampMillis: Long) {
                val bytes = shortArrayToByteArray(chunk, size)
                val amplitude = (computeRms(chunk, size) * AMPLITUDE_GAIN).coerceIn(0f, 1f)
                _currentAmplitude.value = amplitude
                synchronized(chunkBuffer) { chunkBuffer.add(bytes) }
            }
        }
        if (service.isCapturing()) {
            service.stopCapture()
        }
        service.registerListener(listener)
        service.startCapture(streamToVerifier = false)
        activeListener = listener
        isRecording.set(true)
        return true
    }

    fun stopRecording(): ByteArray {
        if (!isRecording.get()) return ByteArray(0)
        val service = serviceProvider() ?: return ByteArray(0)
        service.stopCapture()
        activeListener?.let { service.unregisterListener(it) }
        isRecording.set(false)
        activeListener = null
        _currentAmplitude.value = 0f
        val output = ByteArrayOutputStream()
        synchronized(chunkBuffer) {
            chunkBuffer.forEach { output.write(it) }
            chunkBuffer.clear()
        }
        return output.toByteArray()
    }

    fun cancelRecording() {
        if (!isRecording.get()) return
        val service = serviceProvider() ?: return
        service.stopCapture()
        activeListener?.let { service.unregisterListener(it) }
        isRecording.set(false)
        activeListener = null
        _currentAmplitude.value = 0f
        synchronized(chunkBuffer) { chunkBuffer.clear() }
    }

    suspend fun finalizeEnrollment(samples: List<ByteArray>): Boolean {
        if (samples.isEmpty() || samples.any { it.isEmpty() }) return false
        val averageEnergy = samples.map { computeRms(it) }.average().toFloat()
        if (averageEnergy < MIN_ENERGY_THRESHOLD) return false
        val saved = voiceProfileRepository.saveProfile(samples)
        if (saved) {
            preferenceStore.setVoiceEnrolled(true)
        }
        return saved
    }

    private fun shortArrayToByteArray(chunk: ShortArray, size: Int): ByteArray {
        val buffer = ByteBuffer.allocate(size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        repeat(size) { index -> buffer.putShort(chunk[index]) }
        return buffer.array()
    }

    private fun computeRms(chunk: ShortArray, size: Int): Float {
        if (size == 0) return 0f
        var sumSquares = 0.0
        for (index in 0 until size) {
            val normalized = chunk[index] / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / size).toFloat()
    }

    private fun computeRms(bytes: ByteArray): Float {
        if (bytes.isEmpty()) return 0f
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val remaining = shortBuffer.remaining()
        if (remaining == 0) return 0f
        var sumSquares = 0.0
        val temp = ShortArray(remaining)
        shortBuffer.get(temp)
        temp.forEach { sample ->
            val normalized = sample / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / remaining).toFloat()
    }

    fun estimateDurationSeconds(bytes: ByteArray): Float {
        if (bytes.isEmpty()) return 0f
        val samples = bytes.size / Short.SIZE_BYTES
        return samples.toFloat() / SAMPLE_RATE_HZ
    }

    fun estimateRms(bytes: ByteArray): Float = computeRms(bytes)

    companion object {
        private const val MIN_ENERGY_THRESHOLD = 0.015f
        private const val AMPLITUDE_GAIN = 4f
        const val SAMPLE_RATE_HZ = 16_000f
    }
}
