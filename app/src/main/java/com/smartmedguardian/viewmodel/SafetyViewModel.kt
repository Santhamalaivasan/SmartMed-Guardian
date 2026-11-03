package com.smartmedguardian.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.entity.DrugInteraction
import com.smartmedguardian.data.entity.MedicineHistory
import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.data.entity.UserAllergy
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.util.SafetyAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val repository: MedicineRepository
) : ViewModel() {

    private val safetyAnalyzer = SafetyAnalyzer()

    private val _currentMedicineInfo = MutableStateFlow<MedicineInfo?>(null)
    val currentMedicineInfo: StateFlow<MedicineInfo?> = _currentMedicineInfo.asStateFlow()

    private val _interactions = MutableStateFlow<List<DrugInteraction>>(emptyList())
    val interactions: StateFlow<List<DrugInteraction>> = _interactions.asStateFlow()

    private val _allergies = repository.getAllAllergies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allergies: StateFlow<List<UserAllergy>> = _allergies

    private val _duplicateDoseAlert = MutableStateFlow<String?>(null)
    val duplicateDoseAlert: StateFlow<String?> = _duplicateDoseAlert.asStateFlow()

    private val _allergyAlert = MutableStateFlow<String?>(null)
    val allergyAlert: StateFlow<String?> = _allergyAlert.asStateFlow()

    fun fetchMedicineInfo(name: String) {
        viewModelScope.launch {
            val info = repository.getMedicineInfo(name)
            _currentMedicineInfo.value = info
            if (info != null) {
                checkInteractions(info)
                checkDuplicateDose(info)
                checkAllergies(info)
            }
        }
    }

    private suspend fun checkAllergies(info: MedicineInfo) {
        val userAllergies = repository.getAllAllergies().first()
        for (allergy in userAllergies) {
            if (info.genericName.contains(allergy.allergen, ignoreCase = true) || 
                info.seriousSideEffects.contains(allergy.allergen, ignoreCase = true)) {
                _allergyAlert.value = "Allergy Warning: This medicine contains or relates to ${allergy.allergen}."
                break
            }
        }
    }

    private suspend fun checkDuplicateDose(info: MedicineInfo) {
        val history = repository.getAllHistory().first()
        val canonicalTarget = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(info.medicineName)
        val validStatuses = listOf("CORRECT_MEDICINE", "MATCH")
        
        val lastTaken = history.firstOrNull { item ->
            val itemCanonical = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(item.detectedMedicineName)
            val isNameMatch = com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(itemCanonical, canonicalTarget)
            isNameMatch && validStatuses.contains(item.verificationStatus)
        }
            
        lastTaken?.let {
            val diff = System.currentTimeMillis() - it.timestamp
            if (diff < 4 * 60 * 60 * 1000) { // 4 hours threshold
                _duplicateDoseAlert.value = "Duplicate Dose Warning: This medicine was already verified ${diff / (60 * 1000)} minutes ago."
            } else {
                _duplicateDoseAlert.value = null
            }
        } ?: run {
            val activeIngredient = info.genericName.split("(").first().trim()
            if (activeIngredient.isNotBlank()) {
                val duplicateIngredientTaken = history.firstOrNull { item ->
                    if (!validStatuses.contains(item.verificationStatus)) return@firstOrNull false
                    val itemInfo = repository.getMedicineInfo(item.detectedMedicineName)
                    itemInfo != null && itemInfo.medicineName != info.medicineName &&
                            itemInfo.genericName.contains(activeIngredient, ignoreCase = true) &&
                            (System.currentTimeMillis() - item.timestamp) < 4 * 60 * 60 * 1000
                }
                if (duplicateIngredientTaken != null) {
                    _duplicateDoseAlert.value = "Duplicate Active Ingredient Warning: Another medication containing $activeIngredient (${duplicateIngredientTaken.detectedMedicineName}) was recently verified."
                } else {
                    _duplicateDoseAlert.value = null
                }
            } else {
                _duplicateDoseAlert.value = null
            }
        }
    }

    private suspend fun checkInteractions(info: MedicineInfo) {
        val activePrescriptions = repository.getAllPrescriptions().first()
        val detectedInteractions = mutableListOf<DrugInteraction>()
        
        for (prescription in activePrescriptions) {
            val interaction = repository.checkInteraction(info.medicineName, prescription.medicineName)
            if (interaction != null) {
                detectedInteractions.add(interaction)
            }
        }
        _interactions.value = detectedInteractions
    }

    fun addAllergy(allergen: String) {
        viewModelScope.launch {
            repository.insertAllergy(UserAllergy(allergen))
        }
    }

    fun deleteAllergy(allergy: UserAllergy) {
        viewModelScope.launch {
            repository.deleteAllergy(allergy)
        }
    }

    fun getRecommendations(info: MedicineInfo?, status: com.smartmedguardian.model.VerificationStatus): List<String> {
        return safetyAnalyzer.generateRecommendations(info, status)
    }
}
