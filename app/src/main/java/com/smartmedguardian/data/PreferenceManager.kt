package com.smartmedguardian.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = context.dataStore

    val isVoiceEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[VOICE_ENABLED] ?: true
    }

    val isNotificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED] ?: true
    }

    val reminderSound: Flow<String> = dataStore.data.map { preferences ->
        preferences[REMINDER_SOUND] ?: "default"
    }

    val snoozeDuration: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SNOOZE_DURATION] ?: 5
    }

    val isCaregiverEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CAREGIVER_ENABLED] ?: true
    }

    val isBiometricEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRIC_ENABLED] ?: false
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VOICE_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setReminderSound(sound: String) {
        dataStore.edit { preferences ->
            preferences[REMINDER_SOUND] = sound
        }
    }

    suspend fun setSnoozeDuration(duration: Int) {
        dataStore.edit { preferences ->
            preferences[SNOOZE_DURATION] = duration
        }
    }

    suspend fun setCaregiverEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CAREGIVER_ENABLED] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }

    companion object {
        private val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val REMINDER_SOUND = stringPreferencesKey("reminder_sound")
        private val SNOOZE_DURATION = intPreferencesKey("snooze_duration")
        private val CAREGIVER_ENABLED = booleanPreferencesKey("caregiver_enabled")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }
}
