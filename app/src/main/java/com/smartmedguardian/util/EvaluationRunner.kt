package com.smartmedguardian.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.MatchStatus
import com.smartmedguardian.ml.MedicineClassifier
import com.smartmedguardian.ml.MedicineEmbeddingHelper
import com.smartmedguardian.ml.MedicineReferenceMatcher
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.ml.ReferenceMatchResult
import com.smartmedguardian.model.EvaluationTestCase
import com.smartmedguardian.model.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

object EvaluationRunner {

    private const val TAG = "EvaluationRunner"

    val SUPPORTED_MEDICINE_NAMES = listOf(
        "Adol",
        "Augmentin",
        "Brufen",
        "Cataflam",
        "Claritine",
        "Dolo-650",
        "Flagyl",
        "Glucophage",
        "Paramol",
        "Rennie",
        "Zantac",
        "Zyrtec"
    )

    /**
     * Generates 100 evaluation test cases (50 MATCH, 50 MISMATCH).
     */
    fun generateDefaultTestCases(): List<EvaluationTestCase> {
        val testCases = mutableListOf<EvaluationTestCase>()
        var idCounter = 1

        val medicines = SUPPORTED_MEDICINE_NAMES

        // 50 MATCH Cases (Prescription Medicine == Scanned Medicine)
        for (i in 0 until 50) {
            val med = medicines[i % medicines.size]
            testCases.add(
                EvaluationTestCase(
                    id = idCounter++,
                    prescriptionMedicine = med,
                    scannedMedicineName = med,
                    scannedOcrText = null,
                    expectedMatch = true
                )
            )
        }

        // 50 MISMATCH Cases (Prescription Medicine != Scanned Medicine)
        for (i in 0 until 50) {
            val presMed = medicines[i % medicines.size]
            val scannedMed = medicines[(i + 1) % medicines.size] // Pick a different medicine
            testCases.add(
                EvaluationTestCase(
                    id = idCounter++,
                    prescriptionMedicine = presMed,
                    scannedMedicineName = scannedMed,
                    scannedOcrText = null,
                    expectedMatch = false
                )
            )
        }

        return testCases
    }

    /**
     * Executes a single test case through the real image verification pipeline.
     * If no valid scanned image is provided or loaded, marks the case as unexecuted.
     */
    suspend fun executeTestCase(
        testCase: EvaluationTestCase,
        context: Context,
        verificationManager: MedicineVerificationManager,
        textRecognitionHelper: TextRecognitionHelper,
        classifier: MedicineClassifier,
        embeddingHelper: MedicineEmbeddingHelper,
        referenceMatcher: MedicineReferenceMatcher
    ): EvaluationTestCase = withContext(Dispatchers.IO) {
        val scannedUriStr = testCase.scannedImageUri
        if (scannedUriStr.isNullOrEmpty()) {
            return@withContext testCase.copy(
                executed = false,
                actualMatch = null,
                verificationSource = null,
                verificationStatus = null,
                elapsedTimeMs = null,
                message = "Image Required: Please select a scanned medicine image."
            )
        }

        val scannedBitmap = loadBitmapFromUri(context, scannedUriStr)
        if (scannedBitmap == null) {
            return@withContext testCase.copy(
                executed = false,
                actualMatch = null,
                verificationSource = null,
                verificationStatus = null,
                elapsedTimeMs = null,
                message = "Image Required: Unable to load bitmap from provided Uri ($scannedUriStr)."
            )
        }

        val prescription = Prescription(
            id = 900000L + testCase.id,
            medicineName = testCase.prescriptionMedicine,
            dosage = "1 Tablet",
            frequency = "Daily",
            instructions = "Take as prescribed",
            startDate = System.currentTimeMillis() - 86400000L,
            endDate = System.currentTimeMillis() + 86400000L,
            morning = true,
            afternoon = true,
            evening = true,
            night = true,
            morningTime = "08:00",
            afternoonTime = "13:00",
            eveningTime = "20:00",
            nightTime = "22:00"
        )

        var ocrTextResult: String? = null
        try {
            val textResult = textRecognitionHelper.recognizeText(scannedBitmap)
            if (textResult.rawText.isNotBlank()) {
                ocrTextResult = textResult.rawText
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running ML Kit OCR in image evaluation: ${e.message}", e)
        }

        var cnnResult: RecognitionResult? = null
        try {
            cnnResult = classifier.classify(scannedBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error running CNN classification in image evaluation: ${e.message}", e)
        }

        if (cnnResult == null) {
            cnnResult = RecognitionResult(
                name = "Unknown",
                confidence = 0f,
                inferenceTime = 0L,
                top2Margin = 0f,
                isKnownEnough = false
            )
        }

        var refMatchResult: ReferenceMatchResult? = null
        val refUriStr = testCase.referenceImageUri
        if (!refUriStr.isNullOrEmpty() && embeddingHelper.isModelLoaded()) {
            var refBitmap: Bitmap? = null
            try {
                refBitmap = loadBitmapFromUri(context, refUriStr)
                if (refBitmap != null) {
                    val queryEmb = embeddingHelper.extractEmbedding(scannedBitmap)
                    val refEmb = embeddingHelper.extractEmbedding(refBitmap)
                    val sim = referenceMatcher.calculateCosineSimilarity(queryEmb, refEmb)
                    val status = if (sim >= 0.72f) MatchStatus.MATCH else if (sim >= 0.55f) MatchStatus.UNCERTAIN else MatchStatus.NO_MATCH

                    refMatchResult = ReferenceMatchResult(
                        prescription = prescription,
                        similarity = sim,
                        status = status,
                        matchedImagePath = refUriStr,
                        message = "Reference image evaluation similarity: $sim"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running reference matcher in evaluation: ${e.message}", e)
            } finally {
                refBitmap?.recycle()
            }
        }

        val startNanos = SystemClock.elapsedRealtimeNanos()

        val result = verificationManager.verify(
            recognitionResult = cnnResult,
            prescriptions = listOf(prescription),
            ocrText = ocrTextResult,
            referenceMatchResult = refMatchResult,
            targetMedicine = testCase.prescriptionMedicine,
            targetSlot = "Morning",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now(),
            alreadyVerified = false
        )

        val endNanos = SystemClock.elapsedRealtimeNanos()
        val elapsedTimeMs = (endNanos - startNanos) / 1_000_000.0

        val actualMatch = (result.status == VerificationStatus.CORRECT_MEDICINE)

        scannedBitmap.recycle()

        return@withContext testCase.copy(
            executed = true,
            actualMatch = actualMatch,
            verificationSource = result.verificationSource,
            verificationStatus = result.status,
            elapsedTimeMs = elapsedTimeMs,
            message = result.message
        )
    }

    private fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from uri: $uriString", e)
            null
        }
    }
}
