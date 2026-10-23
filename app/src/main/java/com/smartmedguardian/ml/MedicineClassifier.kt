package com.smartmedguardian.ml

import android.graphics.Bitmap

/**
 * Interface for medicine classification.
 */
interface MedicineClassifier {
    fun classify(bitmap: Bitmap): RecognitionResult
}

/**
 * Implementation of MedicineClassifier using TensorFlow Lite.
 */
class TFLiteMedicineClassifier(private val helper: TensorFlowHelper) : MedicineClassifier {
    override fun classify(bitmap: Bitmap): RecognitionResult {
        return helper.classifyImage(bitmap)
    }
}

/**
 * Fake implementation of MedicineClassifier for standalone unit testing if needed.
 */
class FakeMedicineClassifier : MedicineClassifier {
    override fun classify(bitmap: Bitmap): RecognitionResult {
        return RecognitionResult("Dolo650", 0.98f, 12)
    }
}
