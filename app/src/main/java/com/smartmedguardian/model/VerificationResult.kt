package com.smartmedguardian.model

import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.RecognitionResult

enum class VerificationSource {
    OCR_TEXT,
    REFERENCE_IMAGE,
    CNN_CLASSIFIER,
    UNCERTAIN
}

data class VerificationResult(
    val status: VerificationStatus,
    val recognitionResult: RecognitionResult,
    val prescription: Prescription? = null,
    val message: String? = null,
    val verificationSource: VerificationSource = VerificationSource.UNCERTAIN
)

enum class VerificationStatus {
    CORRECT_MEDICINE,
    MEDICINE_NOT_FOUND,
    UNSCHEDULED_TIME,
    UNABLE_TO_VERIFY,
    UNKNOWN_MEDICINE,
    ALREADY_VERIFIED,
    DUPLICATE_INGREDIENT
}
