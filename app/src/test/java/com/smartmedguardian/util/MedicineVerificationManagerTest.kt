package com.smartmedguardian.util

import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.MatchStatus
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.ml.ReferenceMatchResult
import com.smartmedguardian.model.VerificationSource
import com.smartmedguardian.model.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun scenario1_ocrImageContaining_Dolo650() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Twice daily",
            morning = true,
            evening = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650 PARACETAMOL TABLETS 650 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Dolo-650", result.prescription?.medicineName)
        assertTrue(result.message?.contains("Taken ✓") == true)
    }

    @Test
    fun scenario2_ocrImageContaining_Brufen() {
        val prescription = Prescription(
            id = 2,
            medicineName = "Brufen",
            dosage = "400mg",
            frequency = "Once daily",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Brufen", result.prescription?.medicineName)
        assertTrue(result.message?.contains("Taken ✓") == true)
    }

    @Test
    fun scenario3_ocrImageContaining_Augmentin() {
        val prescription = Prescription(
            id = 3,
            medicineName = "Augmentin",
            dosage = "625mg",
            frequency = "Twice daily",
            morning = true,
            night = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "AUGMENTIN 625",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Augmentin", result.prescription?.medicineName)
        assertTrue(result.message?.contains("Taken ✓") == true)
    }

    @Test
    fun scenario4_ocrText_caseInsensitiveMatching() {
        val matchLower = manager.matchSupportedMedicineFromOcr("dolo 650")
        val matchUpper = manager.matchSupportedMedicineFromOcr("DOLO 650")
        val matchMixed = manager.matchSupportedMedicineFromOcr("DoLo 650")

        assertNotNull(matchLower)
        assertNotNull(matchUpper)
        assertNotNull(matchMixed)
        assertEquals("Dolo-650", matchLower?.canonicalName)
        assertEquals("Dolo-650", matchUpper?.canonicalName)
        assertEquals("Dolo-650", matchMixed?.canonicalName)
    }

    @Test
    fun scenario5_ocrText_hyphenAndSpaceDifferences() {
        val matchHyphen = manager.matchSupportedMedicineFromOcr("Dolo-650")
        val matchSpace = manager.matchSupportedMedicineFromOcr("Dolo 650")
        val matchCompact = manager.matchSupportedMedicineFromOcr("DOLO650")

        assertEquals("Dolo-650", matchHyphen?.canonicalName)
        assertEquals("Dolo-650", matchSpace?.canonicalName)
        assertEquals("Dolo-650", matchCompact?.canonicalName)
    }

    @Test
    fun scenario6_supportedMedicineDifferentFromPrescription_incorrectMedicine() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Twice daily",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertTrue(result.message?.contains("INCORRECT MEDICINE") == true)
        assertTrue(result.message?.contains("Detected: Brufen") == true)
        assertTrue(result.message?.contains("Prescribed: Dolo-650") == true)
    }

    @Test
    fun scenario7_supportedMedicineSameAsPrescription_wrongSchedule() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning only",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            currentTime = LocalTime.of(20, 50), // Evening
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNSCHEDULED_TIME, result.status)
        assertTrue(result.message?.contains("Dolo-650") == true)
    }

    @Test
    fun targetSlotNight_targetTime2000_current2040_validatesNight() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Night",
            night = true,
            nightTime = "20:00",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Night",
            targetTime = "20:00",
            currentTime = LocalTime.of(20, 40), // 8:40 PM
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Night dose") == true)
        assertTrue(result.message?.contains("8:00 PM") == true)
    }

    @Test
    fun targetSlotEvening_targetTime2000_current2040_validatesEvening() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Evening",
            evening = true,
            eveningTime = "20:00",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Evening",
            targetTime = "20:00",
            currentTime = LocalTime.of(20, 40),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Evening dose") == true)
        assertTrue(result.message?.contains("8:00 PM") == true)
    }

    @Test
    fun targetSlotNight_currentPeriodInferenceDoesNotOverrideTargetSlot() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Night",
            night = true,
            nightTime = "20:00",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Night",
            targetTime = "20:00",
            currentTime = LocalTime.of(18, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Night dose") == true)
    }

    @Test
    fun targetSlotEvening_currentPeriodInferenceDoesNotOverrideTargetSlot() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Evening",
            evening = true,
            eveningTime = "20:00",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Evening",
            targetTime = "20:00",
            currentTime = LocalTime.of(20, 40),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Evening dose") == true)
    }

    @Test
    fun noTargetSlot_usesFallbackPeriodInference() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Night",
            night = true,
            nightTime = "20:00",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            currentTime = LocalTime.of(20, 40),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Night dose") == true)
    }

    @Test
    fun verificationOutsideTimeWindow_rejected() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "08:00",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            targetTime = "08:00",
            currentTime = LocalTime.of(18, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNSCHEDULED_TIME, result.status)
        assertTrue(result.message?.contains("Outside Verification Window") == true)
    }

    @Test
    fun scenario8_unsupportedMedicine() {
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
            recognitionResult = RecognitionResult("Unknown", 0.2f, 0),
            prescriptions = listOf(prescription),
            ocrText = "RANDOM UNKNOWN PHARMA DRUG 500 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertTrue(result.message?.contains("UNKNOWN MEDICINE") == true)
    }

    @Test
    fun scenario9_ocrFails_referenceMatchSucceeds() {
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
            similarity = 0.88f,
            status = MatchStatus.MATCH,
            message = "Matched reference image"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.2f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BLURRY UNREADABLE OCR TEXT",
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Dolo-650", result.prescription?.medicineName)
        assertEquals(VerificationSource.REFERENCE_IMAGE, result.verificationSource)
        assertTrue(result.message?.contains("Taken ✓") == true)
    }

    @Test
    fun scenario10_bothOcrAndTFLiteFail_unknownMedicine() {
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
            prescription = null,
            similarity = 0.10f,
            status = MatchStatus.NO_MATCH,
            message = "No reference image match"
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Other", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "UNREADABLE OCR TEXT 123",
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertTrue(result.message?.contains("UNKNOWN MEDICINE") == true)
    }
}
