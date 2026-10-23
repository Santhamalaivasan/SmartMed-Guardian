package com.smartmedguardian.model

import com.smartmedguardian.model.VerificationSource
import com.smartmedguardian.model.VerificationStatus

/**
 * Data class representing an evaluation test case for medicine verification.
 */
data class EvaluationTestCase(
    val id: Int,
    val prescriptionMedicine: String,
    val referenceImageUri: String? = null,
    val scannedMedicineName: String,
    val scannedImageUri: String? = null,
    val scannedOcrText: String? = null,
    val expectedMatch: Boolean, // Determined from test configuration (prescription == scanned)
    val executed: Boolean = false,
    val actualMatch: Boolean? = null, // True if verification status is CORRECT_MEDICINE
    val verificationSource: VerificationSource? = null,
    val verificationStatus: VerificationStatus? = null,
    val elapsedTimeMs: Double? = null,
    val message: String? = null
) {
    val hasValidImageInput: Boolean
        get() = !scannedImageUri.isNullOrBlank()

    val resultClassification: EvaluationResultClass?
        get() {
            if (!executed || actualMatch == null) return null
            return when {
                expectedMatch && actualMatch -> EvaluationResultClass.TP
                !expectedMatch && !actualMatch -> EvaluationResultClass.TN
                !expectedMatch && actualMatch -> EvaluationResultClass.FP
                else -> EvaluationResultClass.FN
            }
        }
}

enum class EvaluationResultClass(val label: String, val description: String) {
    TP("TP", "True Positive (Expected MATCH, Actual MATCH)"),
    TN("TN", "True Negative (Expected MISMATCH, Actual MISMATCH)"),
    FP("FP", "False Positive (Expected MISMATCH, Actual MATCH)"),
    FN("FN", "False Negative (Expected MATCH, Actual MISMATCH)")
}

/**
 * Calculated metrics from executed evaluation test cases.
 */
data class EvaluationMetrics(
    val totalTests: Int = 0,
    val executedTests: Int = 0,
    val unexecutedTests: Int = 0,
    val tp: Int = 0,
    val tn: Int = 0,
    val fp: Int = 0,
    val fn: Int = 0,
    val accuracyPct: Double? = null,
    val precisionPct: Double? = null,
    val recallPct: Double? = null,
    val f1ScorePct: Double? = null,
    val avgVerificationTimeMs: Double? = null,
    val minVerificationTimeMs: Double? = null,
    val maxVerificationTimeMs: Double? = null,
    val medianVerificationTimeMs: Double? = null,
    val ocrSourceCount: Int = 0,
    val refSourceCount: Int = 0,
    val cnnSourceCount: Int = 0,
    val uncertainSourceCount: Int = 0,
    val totalScheduledDoses: Int = 0,
    val confirmedDoses: Int = 0,
    val missedDoses: Int = 0,
    val adherenceRatePct: Double? = null
)
