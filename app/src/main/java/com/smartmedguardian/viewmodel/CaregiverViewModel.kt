package com.smartmedguardian.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.smartmedguardian.data.entity.CaregiverAlert
import com.smartmedguardian.data.entity.CaregiverInfo
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.util.SecurityHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaregiverViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val securityHelper: SecurityHelper
) : ViewModel() {

    val caregiverInfo: StateFlow<CaregiverInfo?> = repository.getCaregiver()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val localAlerts: StateFlow<List<CaregiverAlert>> = repository.getLocalCaregiverAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCaregiver(name: String, relation: String, phone: String, email: String) {
        viewModelScope.launch {
            val fcmToken = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                null
            }
            val encryptedInfo = CaregiverInfo(
                encryptedName = securityHelper.encrypt(name),
                encryptedRelationship = securityHelper.encrypt(relation),
                encryptedPhoneNumber = securityHelper.encrypt(phone),
                encryptedEmail = securityHelper.encrypt(email),
                caregiverFcmToken = fcmToken
            )
            repository.saveCaregiver(encryptedInfo)
        }
    }

    fun deleteCaregiver() {
        viewModelScope.launch {
            repository.deleteCaregiver()
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            repository.acknowledgeCaregiverAlert(alertId)
        }
    }

    fun createTestAlert(eventType: String, medicineName: String, message: String) {
        viewModelScope.launch {
            repository.createCaregiverAlert(eventType, medicineName, message)
        }
    }

    fun getDecryptedName(encrypted: String): String = securityHelper.decrypt(encrypted)
    fun getDecryptedPhone(encrypted: String): String = securityHelper.decrypt(encrypted)

    private val _selectedPatientId = MutableStateFlow<String?>(null)
    
    val remoteRisk = _selectedPatientId.flatMapLatest { id ->
        if (id != null) repository.getRemoteRisk(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val remoteAdherence = _selectedPatientId.flatMapLatest { id ->
        if (id != null) repository.getRemoteAdherence(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remoteAlerts = _selectedPatientId.flatMapLatest { id ->
        if (id != null) repository.getRemoteCaregiverAlerts(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPatient(id: String) {
        _selectedPatientId.value = id
    }
}
