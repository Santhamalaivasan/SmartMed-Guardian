package com.smartmedguardian.util

import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.model.VerificationStatus

class SafetyAnalyzer {

    fun generateRecommendations(
        medicineInfo: MedicineInfo?,
        verificationStatus: VerificationStatus
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (verificationStatus) {
            VerificationStatus.CORRECT_MEDICINE -> recommendations.add("Correct medicine identified.")
            VerificationStatus.ALREADY_VERIFIED -> recommendations.add("Caution: This dose has already been verified for today.")
            VerificationStatus.DUPLICATE_INGREDIENT -> recommendations.add("Caution: Another medication containing the same active ingredient was recently recorded.")
            VerificationStatus.UNSCHEDULED_TIME -> recommendations.add("Caution: This medicine is in your prescription, but is NOT scheduled for this time of day.")
            VerificationStatus.MEDICINE_NOT_FOUND -> recommendations.add("Caution: This medicine is NOT in your current prescription.")
            VerificationStatus.UNABLE_TO_VERIFY, VerificationStatus.UNKNOWN_MEDICINE -> recommendations.add("Warning: Medicine could not be verified as a known prescription. Please retake photo.")
        }

        medicineInfo?.let { info ->
            if (info.beforeAfterFood.isNotBlank()) {
                recommendations.add(info.beforeAfterFood)
            }
            if (info.storageInstructions.isNotBlank()) {
                recommendations.add("Storage: ${info.storageInstructions}")
            }
            recommendations.add("Note: Do not exceed the maximum daily dose of ${info.maxDailyDose}.")
            recommendations.add("Stay hydrated and follow your doctor's advice.")
        }

        return recommendations
    }
}
