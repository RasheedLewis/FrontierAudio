package com.example.frontieraudio.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionSnapshot(
    val recordAudioGranted: Boolean,
    val internetGranted: Boolean,
    val fineLocationGranted: Boolean
) {
    val allCriticalGranted: Boolean
        get() = recordAudioGranted && fineLocationGranted

    val missingPermissions: List<String>
        get() = buildList {
            if (!recordAudioGranted) add(Manifest.permission.RECORD_AUDIO)
            if (!fineLocationGranted) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!internetGranted) add(Manifest.permission.INTERNET)
        }
}

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        recordAudioGranted = isGranted(Manifest.permission.RECORD_AUDIO),
        internetGranted = isGranted(Manifest.permission.INTERNET),
        fineLocationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
    )

    fun missingCriticalPermissions(): List<String> =
        snapshot().missingPermissions.filter { it != Manifest.permission.INTERNET }

    fun shouldRequestCriticalPermissions(): Boolean =
        missingCriticalPermissions().isNotEmpty()

    fun handlePermissionResult(result: Map<String, Boolean>) {
        val snapshot = snapshot()
        Log.i(
            TAG,
            "Permission result processed. recordAudio=${snapshot.recordAudioGranted}, " +
                "fineLocation=${snapshot.fineLocationGranted}, internet=${snapshot.internetGranted}"
        )
        result.filterValues { granted -> !granted }
            .keys
            .forEach { denied ->
                Log.w(TAG, "Permission denied: $denied")
            }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "PermissionManager"
    }
}

