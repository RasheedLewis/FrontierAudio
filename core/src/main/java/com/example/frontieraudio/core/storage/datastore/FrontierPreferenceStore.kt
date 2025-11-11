package com.example.frontieraudio.core.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrontierPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val dataFlow: Flow<Preferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }

    val isFirstLaunch: Flow<Boolean> = dataFlow
        .map { preferences ->
            val hasCompletedFirstLaunch = preferences[Keys.HAS_COMPLETED_FIRST_LAUNCH] ?: false
            !hasCompletedFirstLaunch
        }
        .distinctUntilChanged()

    val isVoiceEnrolled: Flow<Boolean> = dataFlow
        .map { preferences ->
            preferences[Keys.VOICE_ENROLLED] ?: false
        }
        .distinctUntilChanged()

    suspend fun markAppLaunched() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_COMPLETED_FIRST_LAUNCH] = true
        }
    }

    suspend fun setVoiceEnrolled(enrolled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.VOICE_ENROLLED] = enrolled
        }
    }

    private object Keys {
        val HAS_COMPLETED_FIRST_LAUNCH = booleanPreferencesKey("has_completed_first_launch")
        val VOICE_ENROLLED = booleanPreferencesKey("voice_enrolled")
    }
}

