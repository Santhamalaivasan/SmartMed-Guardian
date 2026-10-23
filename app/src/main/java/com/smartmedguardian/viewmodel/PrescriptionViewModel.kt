package com.smartmedguardian.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.MedicineReferenceImage
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrescriptionViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val allPrescriptions: StateFlow<List<Prescription>> = repository.getAllPrescriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPrescription(prescription: Prescription) {
        addPrescriptionWithImages(prescription, emptyList())
    }

    fun addPrescriptionWithImages(prescription: Prescription, imageUris: List<Uri>) {
        viewModelScope.launch {
            val id = repository.insertPrescriptionWithReferenceImages(prescription, imageUris)
            val savedPrescription = prescription.copy(id = id)
            if (savedPrescription.remindersEnabled) {
                reminderScheduler.scheduleReminders(savedPrescription)
            }
        }
    }

    fun updatePrescription(prescription: Prescription) {
        updatePrescriptionWithImages(prescription, emptyList())
    }

    fun updatePrescriptionWithImages(prescription: Prescription, newImageUris: List<Uri>) {
        viewModelScope.launch {
            repository.updatePrescriptionWithReferenceImages(prescription, newImageUris)
            // Cancel old alarms and schedule new ones based on current times and enabled status
            reminderScheduler.cancelAllReminders(prescription.id)
            if (prescription.remindersEnabled) {
                reminderScheduler.scheduleReminders(prescription)
            }
        }
    }

    fun toggleRemindersEnabled(prescription: Prescription, enabled: Boolean) {
        viewModelScope.launch {
            val updated = prescription.copy(remindersEnabled = enabled)
            repository.updatePrescription(updated)
            if (enabled) {
                reminderScheduler.scheduleReminders(updated)
            } else {
                reminderScheduler.cancelAllReminders(prescription.id)
            }
        }
    }

    fun getReferenceImages(prescriptionId: Long): StateFlow<List<MedicineReferenceImage>> {
        return repository.getReferenceImagesForPrescription(prescriptionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun deletePrescription(prescription: Prescription) {
        viewModelScope.launch {
            reminderScheduler.cancelAllReminders(prescription.id)
            repository.deletePrescription(prescription)
        }
    }

    fun markDoseSkipped(prescription: Prescription) {
        viewModelScope.launch {
            repository.insertAdherenceRecord(
                AdherenceRecord(
                    medicineName = prescription.medicineName,
                    scheduledTime = System.currentTimeMillis(),
                    actualTime = null,
                    status = "SKIPPED"
                )
            )
        }
    }
}
