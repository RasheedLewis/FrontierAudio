package com.example.frontieraudio.enrollment

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class VoiceProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val profileDir: File
        get() = File(context.filesDir, VOICE_PROFILE_DIR)

    suspend fun saveProfile(samples: List<ByteArray>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (samples.isEmpty()) error("No samples provided")
            val directory = profileDir
            if (directory.exists()) {
                directory.deleteRecursively()
            }
            directory.mkdirs()
            samples.forEachIndexed { index, data ->
                val clipFile = File(directory, "clip_${index + 1}.pcm")
                clipFile.outputStream().use { stream ->
                    stream.write(data)
                }
            }
        }.isSuccess
    }

    suspend fun loadProfile(): List<File> = withContext(Dispatchers.IO) {
        val directory = profileDir
        if (!directory.exists()) return@withContext emptyList()
        directory.listFiles { file ->
            file.isFile && file.extension.equals("pcm", ignoreCase = true)
        }?.sortedBy { it.name } ?: emptyList()
    }

    suspend fun clearProfile() = withContext(Dispatchers.IO) {
        val directory = profileDir
        if (directory.exists()) {
            directory.deleteRecursively()
        }
    }

    companion object {
        private const val VOICE_PROFILE_DIR = "voice_profile"
    }
}

