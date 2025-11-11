package com.example.frontieraudio.transcriber.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.frontieraudio.core.logging.FrontierLogger
import com.example.frontieraudio.transcriber.R
import com.example.frontieraudio.transcriber.verification.SpeakerVerificationState
import com.example.frontieraudio.transcriber.verification.SpeakerVerifier
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
 
@AndroidEntryPoint
class AudioCaptureService : Service() {

    private val binder = AudioCaptureBinder()
    private val listeners = CopyOnWriteArraySet<AudioChunkListener>()
    private val windowListeners = CopyOnWriteArraySet<AudioWindowListener>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var speakerVerifier: SpeakerVerifier
    @Inject lateinit var logger: FrontierLogger

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var verificationJob: Job? = null
    private var currentConfig: AudioCaptureConfig = AudioCaptureConfig()
    private var streamingPipeline: AudioStreamingPipeline? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val initialNotification = buildNotification(getString(R.string.audio_capture_notification_idle))
        startForeground(NOTIFICATION_ID, initialNotification)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun registerListener(listener: AudioChunkListener) {
        listeners += listener
    }

    fun unregisterListener(listener: AudioChunkListener) {
        listeners -= listener
    }

    fun registerWindowListener(listener: AudioWindowListener) {
        windowListeners += listener
        streamingPipeline?.registerWindowListener(listener)
    }

    fun unregisterWindowListener(listener: AudioWindowListener) {
        windowListeners -= listener
        streamingPipeline?.unregisterWindowListener(listener)
    }

    fun isCapturing(): Boolean = captureJob?.isActive == true

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    @Synchronized
    fun startCapture(config: AudioCaptureConfig = AudioCaptureConfig()) {
        if (isCapturing()) return

        val resolvedConfig = config
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            resolvedConfig.sampleRateInHz,
            resolvedConfig.channelConfig,
            resolvedConfig.audioFormat,
            resolvedConfig.bufferSizeInBytes
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("Failed to initialize AudioRecord for capture.")
        }

        audioRecord.startRecording()
        this.audioRecord = audioRecord
        this.currentConfig = resolvedConfig
        speakerVerifier.reset()
        verificationJob?.cancel()
        verificationJob = serviceScope.launch {
            speakerVerifier.state.collectLatest { state ->
                if (state.timestampMillis > 0L) {
                    logger.i(
                        "Speaker verification status=%s confidence=%.2f at %d",
                        state.status,
                        state.confidence,
                        state.timestampMillis
                    )
                }
            }
        }
        val pipeline = AudioStreamingPipeline(
            windowSizeBytes = resolvedConfig.bufferSizeInBytes
        ) { bytes, timestamp ->
            speakerVerifier.acceptWindow(bytes, timestamp)
        }
        windowListeners.forEach { pipeline.registerWindowListener(it) }
        streamingPipeline = pipeline
        updateNotification(getString(R.string.audio_capture_notification_active))

        captureJob = serviceScope.launch {
            val buffer = ShortArray(resolvedConfig.bufferSizeInShorts)
            while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    val timestamp = System.currentTimeMillis()
                    listeners.forEach { listener ->
                        listener.onAudioChunk(chunk, read, timestamp)
                    }
                    pipeline.appendChunk(chunk, read, timestamp)
                }
            }
            pipeline.flush()
        }
    }

    @Synchronized
    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        verificationJob?.cancel()
        verificationJob = null

        audioRecord?.let { record ->
            try {
                record.stop()
            } catch (_: IllegalStateException) {
                // ignored
            } finally {
                record.release()
            }
        }
        audioRecord = null
        streamingPipeline?.flush()
        streamingPipeline = null
        speakerVerifier.reset()

        updateNotification(getString(R.string.audio_capture_notification_idle))
    }

    inner class AudioCaptureBinder : Binder() {
        val service: AudioCaptureService
            get() = this@AudioCaptureService
        val verificationState: StateFlow<SpeakerVerificationState>
            get() = speakerVerifier.state
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.audio_capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = packageManager?.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            null
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.audio_capture_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_audio_capture)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "frontier_audio_capture"
        private const val NOTIFICATION_ID = 9001
    }
}

