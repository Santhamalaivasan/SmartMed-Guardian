package com.smartmedguardian.util

import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.MatchStatus
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.ml.ReferenceMatchResult
import com.smartmedguardian.model.VerificationSource
import com.smartmedguardian.model.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MedicineVerificationManagerTest {

    private lateinit var manager: MedicineVerificationManager

    @Before
    fun setUp() {
        manager = MedicineVerificationManager()
    }

    // 1. OCR identifies a supported medicine
    @Test
    fun test1_ocrIdentifiesSupportedMedicine() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Augmentin",
            dosage = "625mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "AUGMENTIN 625 MG TABLETS",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Augmentin", result.prescription?.medicineName)
        assertEquals(VerificationSource.OCR_TEXT, result.verificationSource)
    }

    // 2. OCR identifies the wrong medicine
    @Test
    fun test2_ocrIdentifiesWrongMedicine_prescriptionMismatch() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertEquals(VerificationSource.OCR_TEXT, result.verificationSource)
        assertTrue(result.message?.contains("INCORRECT MEDICINE") == true)
    }

    // 3. OCR produces ambiguous text
    @Test
    fun test3_ocrAmbiguousText_fallsThroughToNextStep() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val referenceMatch = ReferenceMatchResult(
            prescription = prescription,
            similarity = 0.85f,
            status = MatchStatus.MATCH,
            message = "Matched reference"
        )

        // Text "TABLETS 500 MG" does not contain any supported medicine token
        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "TABLETS 500 MG PHARMA",
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(VerificationSource.REFERENCE_IMAGE, result.verificationSource)
    }

    // 4. OCR produces no text
    @Test
    fun test4_ocrNoText_fallsThroughToNextStep() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Flagyl",
            dosage = "500mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val referenceMatch = ReferenceMatchResult(
            prescription = prescription,
            similarity = 0.80f,
            status = MatchStatus.MATCH,
            message = "Matched reference"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "",
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(VerificationSource.REFERENCE_IMAGE, result.verificationSource)
    }

    // 5. Reference image produces a match
    @Test
    fun test5_referenceImageProducesMatch() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Claritine",
            dosage = "10mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val referenceMatch = ReferenceMatchResult(
            prescription = prescription,
            similarity = 0.82f,
            status = MatchStatus.MATCH,
            message = "High similarity match"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.2f, 0),
            prescriptions = listOf(prescription),
            ocrText = null,
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(VerificationSource.REFERENCE_IMAGE, result.verificationSource)
        assertEquals(0.82f, result.recognitionResult.confidence, 0.01f)
    }

    // 6. Reference image produces an uncertain result
    @Test
    fun test6_referenceImageUncertain_returnsUnableToVerify() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Zyrtec",
            dosage = "10mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val referenceMatch = ReferenceMatchResult(
            prescription = prescription,
            similarity = 0.60f, // Between 0.55 and 0.72
            status = MatchStatus.UNCERTAIN,
            message = "Uncertain match"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.2f, 0),
            prescriptions = listOf(prescription),
            ocrText = null,
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNABLE_TO_VERIFY, result.status)
        assertEquals(VerificationSource.REFERENCE_IMAGE, result.verificationSource)
    }

    // 7. CNN correctly identifies a medicine
    @Test
    fun test7_cnnCorrectlyIdentifiesMedicine() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Glucophage",
            dosage = "500mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult(
                name = "Glucophage",
                confidence = 0.92f,
                inferenceTime = 15,
                top2Margin = 0.40f,
                isKnownEnough = true
            ),
            prescriptions = listOf(prescription),
            ocrText = null,
            referenceMatchResult = null,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(VerificationSource.CNN_CLASSIFIER, result.verificationSource)
        assertEquals("Glucophage", result.prescription?.medicineName)
    }

    // 8. CNN returns low confidence
    @Test
    fun test8_cnnLowConfidence_returnsUnknown() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Glucophage",
            dosage = "500mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult(
                name = "Glucophage",
                confidence = 0.55f, // Below 0.70 threshold
                inferenceTime = 15,
                top2Margin = 0.10f,
                isKnownEnough = false
            ),
            prescriptions = listOf(prescription),
            ocrText = null,
            referenceMatchResult = null,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertEquals(VerificationSource.UNCERTAIN, result.verificationSource)
    }

    // 9. Unsupported medicine returns unknown
    @Test
    fun test9_unsupportedMedicine_returnsUnknown() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Adol",
            dosage = "500mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Other", 0.20f, 10, top2Margin = 0.05f, isKnownEnough = false),
            prescriptions = listOf(prescription),
            ocrText = "RANDOM PHARMACEUTICAL DRUG FORMULATION",
            referenceMatchResult = null,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertEquals(VerificationSource.UNCERTAIN, result.verificationSource)
    }

    // 10. All verification stages fail safely
    @Test
    fun test10_allStagesFailSafely() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Paramol",
            dosage = "500mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val referenceMatch = ReferenceMatchResult(
            prescription = null,
            similarity = 0.15f,
            status = MatchStatus.NO_MATCH,
            message = "No match"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.10f, 12, top2Margin = 0.01f, isKnownEnough = false),
            prescriptions = listOf(prescription),
            ocrText = "BLURRY UNREADABLE PACKAGING TEXT 999",
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertEquals(VerificationSource.UNCERTAIN, result.verificationSource)
        assertTrue(result.message?.contains("UNKNOWN MEDICINE") == true)
    }

    // 11. Dolo650 and Dolo-650 aliases work correctly
    @Test
    fun test11_dolo650AndDolo650AliasesWorkCorrectly() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        // Query with DOLO650 OCR
        val resultOcr = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO650 TABLETS",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )
        assertEquals(VerificationStatus.CORRECT_MEDICINE, resultOcr.status)
        assertEquals("Dolo-650", resultOcr.prescription?.medicineName)

        // Query with Dolo650 CNN model output
        val resultCnn = manager.verify(
            recognitionResult = RecognitionResult("Dolo650", 0.95f, 10, top2Margin = 0.50f, isKnownEnough = true),
            prescriptions = listOf(prescription),
            ocrText = null,
            referenceMatchResult = null,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )
        assertEquals(VerificationStatus.CORRECT_MEDICINE, resultCnn.status)
        assertEquals("Dolo-650", resultCnn.prescription?.medicineName)
    }

    // 12. Prescription mismatch is detected
    @Test
    fun test12_prescriptionMismatchDetected() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Zantac",
            dosage = "20mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "CATAFLAM 50 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertEquals(VerificationSource.OCR_TEXT, result.verificationSource)
        assertTrue(result.message?.contains("INCORRECT MEDICINE") == true)
        assertTrue(result.message?.contains("Detected: Cataflam") == true)
    }

    @Test
    fun testOcrPriorityOverReferenceMatching() {
        val prescriptionAdol = Prescription(
            id = 1,
            medicineName = "Adol",
            dosage = "500mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val prescriptionAugmentin = Prescription(
            id = 2,
            medicineName = "Augmentin",
            dosage = "625mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        // OCR detects "Adol" clearly, while Reference Image Match claims a match for "Augmentin"
        val referenceMatchAugmentin = ReferenceMatchResult(
            prescription = prescriptionAugmentin,
            similarity = 0.80f,
            status = MatchStatus.MATCH,
            message = "Reference match"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = listOf(prescriptionAdol, prescriptionAugmentin),
            ocrText = "ADOL 500 MG TABLETS",
            referenceMatchResult = referenceMatchAugmentin,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        // OCR (Priority 1) MUST prevail over Reference Match (Priority 2)
        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Adol", result.prescription?.medicineName)
        assertEquals(VerificationSource.OCR_TEXT, result.verificationSource)
    }
}
