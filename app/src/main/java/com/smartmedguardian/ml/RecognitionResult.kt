package com.smartmedguardian.ml

/**
 * Data class representing the result of a medicine recognition.
 *
 * @property name The name of the predicted medicine.
 * @property confidence The confidence score of the prediction (0.0 to 1.0).
 * @property inferenceTime The time taken to run the inference in milliseconds.
 * @property top2Margin The difference between top-1 and top-2 confidence scores.
 * @property isKnownEnough Whether the prediction passes closed-set safety thresholds.
 */
data class RecognitionResult(
    val name: String,
    val confidence: Float,
    val inferenceTime: Long,
    val top2Margin: Float = 1.0f,
    val isKnownEnough: Boolean = true
)
