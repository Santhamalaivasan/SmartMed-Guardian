package com.smartmedguardian.util

import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.model.RiskAssessment
import com.smartmedguardian.model.RiskLevel

class RiskEngine {

    fun assessRisk(
        records: List<AdherenceRecord>,
        prescriptions: List<Prescription>
    ): RiskAssessment {
        var score = 0
        val reasons = mutableListOf<String>()

        if (records.isEmpty()) {
            return RiskAssessment(RiskLevel.LOW, 0, listOf("No adherence data available yet."))
        }

        // 1. Calculate general adherence percentage (last 30 records)
        val recentRecords = records.take(30)
        val takenCount = recentRecords.count { it.status == "ON_TIME" || it.status == "TAKEN" || it.status == "LATE" }
        val adherencePercent = (takenCount.toFloat() / recentRecords.size * 100).toInt()

        if (adherencePercent < 60) {
            score += 40
            reasons.add("Overall adherence is very low ($adherencePercent%).")
        } else if (adherencePercent < 85) {
            score += 20
            reasons.add("Adherence could be improved ($adherencePercent%).")
        }

        // 2. Check for consecutive missed doses
        var consecutiveMissed = 0
        var maxConsecutiveMissed = 0
        for (record in records) {
            if (record.status == "MISSED") {
                consecutiveMissed++
                maxConsecutiveMissed = maxOf(maxConsecutiveMissed, consecutiveMissed)
            } else {
                consecutiveMissed = 0
            }
        }

        if (maxConsecutiveMissed >= 3) {
            score += 50
            reasons.add("User missed $maxConsecutiveMissed consecutive doses.")
        } else if (maxConsecutiveMissed >= 2) {
            score += 25
            reasons.add("User missed 2 consecutive doses.")
        }

        // 3. Check for high-risk medications (simplified logic)
        val highRiskMedicines = listOf("Warfarin", "Insulin", "Digoxin", "Metformin")
        val missedHighRisk = recentRecords.any { 
            it.status == "MISSED" && highRiskMedicines.any { hrm -> it.medicineName.contains(hrm, ignoreCase = true) } 
        }

        if (missedHighRisk) {
            score += 30
            reasons.add("Critical medication doses were missed.")
        }

        // 4. Number of medicines (Complexity risk)
        if (prescriptions.size > 5) {
            score += 10
            reasons.add("User has a complex regimen (${prescriptions.size} medicines).")
        }

        val finalScore = minOf(score, 100)
        val level = when {
            finalScore >= 80 -> RiskLevel.VERY_HIGH
            finalScore >= 50 -> RiskLevel.HIGH
            finalScore >= 25 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        if (reasons.isEmpty() && finalScore < 25) {
            reasons.add("Medication is being taken consistently.")
        }

        return RiskAssessment(level, finalScore, reasons)
    }
}
