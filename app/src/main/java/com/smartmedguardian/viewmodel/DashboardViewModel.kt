package com.smartmedguardian.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.model.RiskAssessment
import com.smartmedguardian.model.RiskLevel
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.util.AdherencePredictor
import com.smartmedguardian.util.AdherenceTrend
import com.smartmedguardian.util.InsightGenerator
import com.smartmedguardian.util.RiskEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MedicineRepository
) : ViewModel() {

    private val riskEngine = RiskEngine()
    private val insightGenerator = InsightGenerator()
    private val adherencePredictor = AdherencePredictor()

    val prescriptions: StateFlow<List<Prescription>> = repository.getAllPrescriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adherenceRecords: StateFlow<List<AdherenceRecord>> = repository.getAllAdherenceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val riskAssessment: StateFlow<RiskAssessment> = combine(adherenceRecords, prescriptions) { records, prescs ->
        val risk = riskEngine.assessRisk(records, prescs)
        viewModelScope.launch {
            try {
                repository.uploadRisk(risk)
            } catch (e: Exception) {
                // Background upload error handling
            }
        }
        risk
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RiskAssessment(RiskLevel.LOW, 0, emptyList()))

    val missedDoseProbability: StateFlow<Float> = adherenceRecords.map {
        adherencePredictor.predictMissedDoseProbability(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val adherenceTrend: StateFlow<AdherenceTrend> = adherenceRecords.map {
        adherencePredictor.getAdherenceTrend(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdherenceTrend.STABLE)

    val insights: StateFlow<List<String>> = adherenceRecords.map {
        insightGenerator.generateInsights(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
