package com.smartmedguardian.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.smartmedguardian.data.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.smartmedguardian.repository.MedicineRepository
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val repository: MedicineRepository
) : ViewModel() {

    private val auth: FirebaseAuth? by lazy {
        try {
            Firebase.auth
        } catch (e: Exception) {
            null
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow(auth?.currentUser)
    val currentUser = _currentUser.asStateFlow()

    val isBiometricEnabled: StateFlow<Boolean> = preferenceManager.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun signUp(email: String, pass: String) {
        val authInstance = auth
        if (authInstance == null) {
            _authState.value = AuthState.Error("Firebase not initialized. Check google-services.json")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authInstance.createUserWithEmailAndPassword(email, pass).await()
                _currentUser.value = authInstance.currentUser
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }

    fun login(email: String, pass: String) {
        val authInstance = auth
        if (authInstance == null) {
            _authState.value = AuthState.Error("Firebase not initialized. Check google-services.json")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authInstance.signInWithEmailAndPassword(email, pass).await()
                _currentUser.value = authInstance.currentUser
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                repository.clearLocalUserData()
                auth?.signOut()
                _currentUser.value = null
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Logout cleanup failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun resetPassword(email: String) {
        val authInstance = auth ?: return
        viewModelScope.launch {
            try {
                authInstance.sendPasswordResetEmail(email).await()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun loginDemo() {
        _authState.value = AuthState.Success
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
