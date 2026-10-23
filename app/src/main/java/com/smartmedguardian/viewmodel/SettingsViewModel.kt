package com.smartmedguardian.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    private val _isDarkMode = mutableStateOf<Boolean?>(null) // null means follow system
    val isDarkMode: State<Boolean?> = _isDarkMode

    val isVoiceEnabled: StateFlow<Boolean> = preferenceManager.isVoiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isNotificationsEnabled: StateFlow<Boolean> = preferenceManager.isNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isCaregiverEnabled: StateFlow<Boolean> = preferenceManager.isCaregiverEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBiometricEnabled: StateFlow<Boolean> = preferenceManager.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDarkMode(enabled: Boolean?) {
        _isDarkMode.value = enabled
    }

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setVoiceEnabled(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setNotificationsEnabled(enabled)
        }
    }

    fun setCaregiverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setCaregiverEnabled(enabled)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setBiometricEnabled(enabled)
        }
    }
}
