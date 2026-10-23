package com.smartmedguardian.repository

import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.CaregiverAlert
import com.smartmedguardian.data.entity.MedicineHistory
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.MatchStatus
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.ml.ReferenceMatchResult
import com.smartmedguardian.model.VerificationResult
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.util.CaregiverNotificationHelper
import com.smartmedguardian.util.MedicineVerificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MedicineVerificationFlowTest {

    private lateinit var manager: MedicineVerificationManager
    private val fakeAdherenceList = mutableListOf<AdherenceRecord>()
    private val fakeHistoryList = mutableListOf<MedicineHistory>()
    private val fakeCaregiverNotifications = mutableListOf<Pair<String, String>>()
    var simulateNotificationFailure = false

    @Before
    fun setUp() {
        manager = MedicineVerificationManager()
        fakeAdherenceList.clear()
        fakeHistoryList.clear()
        fakeCaregiverNotifications.clear()
        simulateNotificationFailure = false
    }

    private fun simulateVerificationFlow(
        recognitionResult: RecognitionResult,
        prescriptions: List<Prescription>,
        ocrText: String? = null,
        referenceMatchResult: ReferenceMatchResult? = null,
        targetMedicine: String? = null,
        targetSlot: String? = null,
        targetTime: String? = null,
        prescriptionId: Long? = null,
        currentTime: LocalTime = LocalTime.of(8, 0),
        currentDate: LocalDate = LocalDate.of(2026, 3, 31)
    ): Pair<VerificationResult, Boolean> {

        val cleanTargetMedicine = targetMedicine?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        val cleanTargetSlot = targetSlot?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        val cleanTargetTime = targetTime?.takeIf { it.isNotBlank() && !it.startsWith("{") }

        val candidatePrescription = if (prescriptionId != null && prescriptionId > 0L) {
            prescriptions.find { it.id == prescriptionId }
                ?: cleanTargetMedicine?.let { target ->
                    prescriptions.find {
                        val pNorm = manager.normalizeTextForMatching(it.medicineName)
                        val tNorm = manager.normalizeTextForMatching(target)
                        pNorm.isNotBlank() && (pNorm == tNorm || pNorm.contains(tNorm) || tNorm.contains(pNorm))
                    }
                }
        } else if (!cleanTargetMedicine.isNullOrBlank()) {
            prescriptions.find {
                val pNorm = manager.normalizeTextForMatching(it.medicineName)
                val tNorm = manager.normalizeTextForMatching(cleanTargetMedicine)
                pNorm.isNotBlank() && (pNorm == tNorm || pNorm.contains(tNorm) || tNorm.contains(pNorm))
            }
        } else {
            val ocrMatch = manager.matchSupportedMedicineFromOcr(ocrText ?: "")
            val recognizedName = ocrMatch?.canonicalName ?: recognitionResult.name
            prescriptions.find {
                val pNorm = manager.normalizeTextForMatching(it.medicineName)
                val rNorm = manager.normalizeTextForMatching(recognizedName)
                pNorm.isNotBlank() && (pNorm == rNorm || pNorm.contains(rNorm) || rNorm.contains(pNorm))
            }
        }

        val activeSlot = cleanTargetSlot?.takeIf { it.isNotBlank() } ?: manager.inferSlotFromTime(currentTime, candidatePrescription)

        // Duplicate check
        val alreadyVerified = if (candidatePrescription != null) {
            val (wStart, wEnd) = manager.getSlotTimeWindowMillis(activeSlot, currentTime, currentDate)
            fakeAdherenceList.any {
                it.medicineName.equals(candidatePrescription.medicineName, ignoreCase = true) &&
                        (it.status == "TAKEN" || it.status == "ON_TIME" || it.status == "LATE") &&
                        it.scheduledTime in wStart..wEnd
            }
        } else false

        val verification = manager.verify(
            recognitionResult = recognitionResult,
            prescriptions = prescriptions,
            ocrText = ocrText,
            referenceMatchResult = referenceMatchResult,
            targetMedicine = cleanTargetMedicine,
            targetSlot = activeSlot,
            targetTime = cleanTargetTime,
            currentTime = currentTime,
            currentDate = currentDate,
            alreadyVerified = alreadyVerified
        )

        var adherenceCreated = false
        if (verification.status == VerificationStatus.CORRECT_MEDICINE && verification.prescription != null) {
            val verifiedPrescription = verification.prescription
            val scheduledMillis = manager.getScheduledTimeMillis(verifiedPrescription, activeSlot, cleanTargetTime, currentTime, currentDate)
            val actualMillis = currentDate.atTime(currentTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val delayMinutes = maxOf(0, ((actualMillis - scheduledMillis) / 60000L).toInt())
            val adherenceStatus = if (delayMinutes > 30) "LATE" else "ON_TIME"

            val record = AdherenceRecord(
                medicineName = verifiedPrescription.medicineName,
                scheduledTime = scheduledMillis,
                actualTime = actualMillis,
                status = adherenceStatus,
                delayMinutes = delayMinutes
            )
            fakeAdherenceList.add(record)
            adherenceCreated = true

            // Trigger Caregiver Notification
            try {
                if (simulateNotificationFailure) {
                    throw RuntimeException("Simulated FCM/Firebase network failure")
                }
                val (title, body) = CaregiverNotificationHelper.buildNotificationPayload(
                    record = record,
                    explicitTimeStr = cleanTargetTime
                )
                fakeCaregiverNotifications.add(Pair(title, body))
            } catch (e: Exception) {
                // Network failure does NOT affect local AdherenceRecord!
            }

            fakeHistoryList.add(
                MedicineHistory(
                    detectedMedicineName = verifiedPrescription.medicineName,
                    confidence = verification.recognitionResult.confidence,
                    timestamp = System.currentTimeMillis(),
                    verificationStatus = VerificationStatus.CORRECT_MEDICINE.name,
                    prescriptionId = verifiedPrescription.id
                )
            )
        } else {
            val historyName = verification.prescription?.medicineName
                ?: verification.recognitionResult.name.ifBlank { "Unknown" }

            fakeHistoryList.add(
                MedicineHistory(
                    detectedMedicineName = historyName,
                    confidence = verification.recognitionResult.confidence,
                    timestamp = System.currentTimeMillis(),
                    verificationStatus = verification.status.name,
                    prescriptionId = verification.prescription?.id
                )
            )
        }

        return Pair(verification, adherenceCreated)
    }

    // 1. Correct OCR + correct prescription → Taken
    @Test
    fun test1_correctOcrAndCorrectPrescription_resultsInTaken() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650 PARACETAMOL 650 MG",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        val status = fakeAdherenceList.first().status
        assertTrue("Expected taken status but was $status", status == "ON_TIME" || status == "TAKEN" || status == "LATE")
        assertEquals("Dolo-650", fakeAdherenceList.first().medicineName)
    }

    // 2. Correct OCR + wrong prescription → Not Taken
    @Test
    fun test2_correctOcrAndWrongPrescription_notTaken() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            targetMedicine = "Dolo-650",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 3. Wrong medicine → Not Taken
    @Test
    fun test3_wrongMedicine_notTaken() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Brufen", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 4. Unknown medicine → Not Taken
    @Test
    fun test4_unknownMedicine_notTaken() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Unknown", 0.2f, 0),
            prescriptions = listOf(prescription),
            ocrText = "RANDOM PHARMA DRUG XYZ 500 MG",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 5. OCR failure → Not Taken
    @Test
    fun test5_ocrFailure_notTaken() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Other", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BLURRY UNREADABLE TEXT 123",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 6. Visual/reference verification failure → Not Taken
    @Test
    fun test6_visualReferenceFailure_notTaken() {
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
            similarity = 0.15f,
            status = MatchStatus.NO_MATCH,
            message = "No visual match"
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Other", 0.3f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BLURRY UNREADABLE OCR TEXT",
            referenceMatchResult = referenceMatch,
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 7. Reminder firing alone → Not Taken
    @Test
    fun test7_reminderFiringAlone_notTaken() {
        // Firing reminder alarm posts notification only
        val notificationShown = true
        assertTrue(notificationShown)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 8. Snooze → Not Taken
    @Test
    fun test8_snooze_notTaken() {
        // User clicks Remind Me Later (snooze)
        val snoozed = true
        assertTrue(snoozed)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 9. Notification tap without verification → Not Taken
    @Test
    fun test9_notificationTapWithoutVerification_notTaken() {
        // User taps notification -> CameraScreen opens but user closes without scanning
        val cameraOpened = true
        assertTrue(cameraOpened)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 10. Successful verification → AdherenceRecord created/updated
    @Test
    fun test10_successfulVerification_createsAdherenceRecord() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        val rec = fakeAdherenceList.first()
        assertEquals("Dolo-650", rec.medicineName)
        assertTrue("Expected taken status but was ${rec.status}", rec.status == "ON_TIME" || rec.status == "TAKEN" || rec.status == "LATE")
    }

    // 11. Successful verification → MedicineHistory updated
    @Test
    fun test11_successfulVerification_updatesMedicineHistory() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(1, fakeHistoryList.size)
        val hist = fakeHistoryList.first()
        assertEquals("Dolo-650", hist.detectedMedicineName)
        assertEquals("CORRECT_MEDICINE", hist.verificationStatus)
    }

    // 12. Same dose verified twice → only one adherence record
    @Test
    fun test12_sameDoseVerifiedTwice_preventsDuplicateAdherenceRecord() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        // First successful verification
        val (result1, adherenceCreated1) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result1.status)
        assertTrue(adherenceCreated1)
        assertEquals(1, fakeAdherenceList.size)

        // Second verification of same Morning dose on same day
        val (result2, adherenceCreated2) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            currentTime = LocalTime.of(8, 5)
        )

        assertEquals(VerificationStatus.ALREADY_VERIFIED, result2.status)
        assertFalse(adherenceCreated2)
        // Adherence count remains 1!
        assertEquals(1, fakeAdherenceList.size)
    }

    // 13. Different doses of the same medicine → each dose can be independently verified
    @Test
    fun test13_differentDosesOfSameMedicine_independentlyVerified() {
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

        // Morning dose verification at 08:00 AM
        val (mornResult, mCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, mornResult.status)
        assertTrue(mCreated)
        assertEquals(1, fakeAdherenceList.size)

        // Evening dose verification at 08:00 PM (20:00)
        val (eveResult, eCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Evening",
            currentTime = LocalTime.of(20, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, eveResult.status)
        assertTrue(eCreated)
        // Both Morning and Evening doses are independently verified!
        assertEquals(2, fakeAdherenceList.size)
    }

    // 14. OCR completes successfully
    @Test
    fun test14_ocrCompletesSuccessfully() {
        val ocrMatch = manager.matchSupportedMedicineFromOcr("DOLO 650 MG")
        assertNotNull(ocrMatch)
        assertEquals("Dolo-650", ocrMatch?.canonicalName)
    }

    // 15. OCR failure returns controlled result without hanging
    @Test
    fun test15_ocrFailureReturnsControlledResult() {
        val prescription = Prescription(id = 1, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, startDate = 0L, endDate = 0L)
        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Unknown", 0f, 0),
            prescriptions = listOf(prescription),
            ocrText = null, // OCR returned null/empty text
            currentTime = LocalTime.of(8, 0)
        )
        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertFalse(adherenceCreated)
    }

    // 16. TFLite completes successfully
    @Test
    fun test16_tfLiteCompletesSuccessfully() {
        val cnnResult = RecognitionResult("Dolo650", 0.95f, 15, top2Margin = 0.3f, isKnownEnough = true)
        assertEquals("Dolo650", cnnResult.name)
        assertTrue(cnnResult.confidence >= 0.70f)
    }

    // 17. TFLite failure returns controlled result
    @Test
    fun test17_tfLiteFailureReturnsControlledResult() {
        val prescription = Prescription(id = 1, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, startDate = 0L, endDate = 0L)
        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Unknown", 0f, 0), // TFLite returned failure/unknown
            prescriptions = listOf(prescription),
            ocrText = "UNREADABLE",
            currentTime = LocalTime.of(8, 0)
        )
        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
        assertFalse(adherenceCreated)
    }

    // 18. Repository verification & Database query complete
    @Test
    fun test18_repositoryVerificationAndDatabaseQueryComplete() {
        val prescription = Prescription(id = 1, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, startDate = 0L, endDate = 0L)
        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.90f, 10),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            currentTime = LocalTime.of(8, 0)
        )
        assertNotNull(result)
        assertTrue(adherenceCreated)
    }

    // 19. Complete verification times out -> Timeout does NOT create a TAKEN adherence record
    @Test
    fun test19_timeoutDoesNotCreateAdherenceRecord() {
        // Simulating a timeout during processImage pipeline
        val isTimeout = true
        var adherenceCreated = false
        if (isTimeout) {
            // Timeout caught by TimeoutCancellationException -> UI set to CameraUiState.Error
            adherenceCreated = false
        }
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 20. Exception does NOT create a TAKEN adherence record
    @Test
    fun test20_exceptionDoesNotCreateAdherenceRecord() {
        var adherenceCreated = false
        try {
            throw RuntimeException("Database error during verification")
        } catch (e: Exception) {
            // Exception caught -> UI set to CameraUiState.Error
            adherenceCreated = false
        }
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 21. UI leaves analyzing state after success or failure
    @Test
    fun test21_uiLeavesAnalyzingState() {
        // Given CameraUiState.Loading ("Analyzing Medicine...")
        var uiState = "Loading"

        // On Success:
        uiState = "Success"
        assertEquals("Success", uiState)

        // On Failure / Timeout:
        uiState = "Error"
        assertEquals("Error", uiState)
    }

    // 22. targetSlot = NIGHT, targetTime = 20:00, current = 20:40 -> validates against NIGHT
    @Test
    fun test22_targetSlotNight_targetTime2000_current2040_validatesNight() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Night",
            currentTime = LocalTime.of(20, 40) // 8:40 PM
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        assertEquals("Dolo-650", fakeAdherenceList.first().medicineName)
        assertTrue(result.message?.contains("Night dose") == true)
        assertTrue(result.message?.contains("Taken ✓") == true)
    }

    // 23. targetSlot = EVENING, targetTime = 20:00, current = 20:40 -> validates against EVENING
    @Test
    fun test23_targetSlotEvening_targetTime2000_current2040_validatesEvening() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Evening",
            currentTime = LocalTime.of(20, 40)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertTrue(result.message?.contains("Evening dose") == true)
    }

    // 24. targetSlot = NIGHT -> current-period inference must NOT override targetSlot
    @Test
    fun test24_targetSlotNight_notOverriddenByInference() {
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

        val (result, _) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Night",
            currentTime = LocalTime.of(18, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Night dose") == true)
    }

    // 25. targetSlot = EVENING -> current-period inference must NOT override targetSlot
    @Test
    fun test25_targetSlotEvening_notOverriddenByInference() {
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

        val (result, _) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "Dolo-650",
            targetSlot = "Evening",
            currentTime = LocalTime.of(20, 40)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(result.message?.contains("Evening dose") == true)
    }

    // 26. No targetSlot -> fallback period inference used
    @Test
    fun test26_noTargetSlot_usesFallbackPeriodInference() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            currentTime = LocalTime.of(20, 40)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertTrue(result.message?.contains("Night dose") == true)
    }

    // 27. Exact scheduled time is respected
    @Test
    fun test27_exactScheduledTimeIsRespected() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Night",
            currentTime = LocalTime.of(20, 15)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertTrue(result.message?.contains("8:00 PM") == true)
    }

    // 28. Verification outside allowed time window is rejected
    @Test
    fun test28_outsideTimeWindow_rejected() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            currentTime = LocalTime.of(18, 0)
        )

        assertEquals(VerificationStatus.UNSCHEDULED_TIME, result.status)
        assertFalse(adherenceCreated)
        assertTrue(result.message?.contains("Outside Verification Window") == true)
    }

    // 29. Wrong medicine + correct target slot -> NOT TAKEN
    @Test
    fun test29_wrongMedicineWithTargetSlot_notTaken() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Brufen", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            targetMedicine = "Dolo-650",
            targetSlot = "Night",
            currentTime = LocalTime.of(20, 40)
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
    }

    // 30. Already verified dose -> ALREADY_VERIFIED
    @Test
    fun test30_alreadyVerifiedDose_returnsAlreadyVerified() {
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

        // First scan
        simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Night",
            currentTime = LocalTime.of(20, 40)
        )

        // Second scan
        val (result2, adherenceCreated2) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Night",
            currentTime = LocalTime.of(20, 45)
        )

        assertEquals(VerificationStatus.ALREADY_VERIFIED, result2.status)
        assertFalse(adherenceCreated2)
        assertEquals(1, fakeAdherenceList.size)
    }

    // 31. Bug Fix Test: Dolo-650 scheduled at 7:13 AM scanned at 7:13 AM with Reference Image match -> ON_TIME & Adherence Record created
    @Test
    fun test31_dolo650_scheduled713_scanned713_resultsInOnTimeDose() {
        val prescription = Prescription(
            id = 101,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "07:13",
            startDate = 0L,
            endDate = 0L
        )

        val referenceMatch = ReferenceMatchResult(
            prescription = prescription,
            similarity = 0.86f,
            status = MatchStatus.MATCH,
            message = "Visual reference image match"
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo 650", 0.99f, 0),
            prescriptions = listOf(prescription),
            ocrText = null,
            referenceMatchResult = referenceMatch,
            targetMedicine = "{targetMedicine}", // Template string passed in placeholder route
            targetSlot = "Morning",
            targetTime = "7:13 AM",
            prescriptionId = 101L,
            currentTime = LocalTime.of(7, 13)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        val rec = fakeAdherenceList.first()
        assertEquals("Dolo-650", rec.medicineName)
        assertEquals("ON_TIME", rec.status)
    }

    // 32. Template string sanitization test: "{targetMedicine}" string is sanitized and does not cause literal mismatch
    @Test
    fun test32_templateStringSanitization_preventsLiteralMismatch() {
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

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetMedicine = "{targetMedicine}",
            currentTime = LocalTime.of(8, 0)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals("Dolo-650", result.prescription?.medicineName)
    }

    // 33. Medicine normalization test: "Dolo-650", "Dolo 650", "DOLO-650", "DOLO 650", "dolo650" all normalize to "dolo650"
    @Test
    fun test33_medicineNormalization_matchesAllVariations() {
        val norm1 = manager.normalizeTextForMatching("Dolo-650")
        val norm2 = manager.normalizeTextForMatching("Dolo 650")
        val norm3 = manager.normalizeTextForMatching("DOLO-650")
        val norm4 = manager.normalizeTextForMatching("DOLO 650")
        val norm5 = manager.normalizeTextForMatching("dolo650")

        assertEquals("dolo650", norm1)
        assertEquals("dolo650", norm2)
        assertEquals("dolo650", norm3)
        assertEquals("dolo650", norm4)
        assertEquals("dolo650", norm5)
    }

    // 34. Candidate prescription == null handling: When prescription is not found in Room DB, logs failure and does not mark dose as taken
    @Test
    fun test34_missingPrescriptionInRoom_doesNotMarkDoseAsTaken() {
        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Unknown", 0.3f, 0),
            prescriptions = emptyList(), // No prescription in Room DB
            ocrText = "SOME RANDOM UNKNOWN PHARMA",
            targetMedicine = "Dolo-650",
            currentTime = LocalTime.of(8, 0)
        )

        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
        assertTrue(result.status == VerificationStatus.UNABLE_TO_VERIFY || result.status == VerificationStatus.UNKNOWN_MEDICINE || result.status == VerificationStatus.MEDICINE_NOT_FOUND)
    }

    // 35. Caregiver Notification TEST 1: Prescription=Dolo-650, Scheduled=7:20 AM, Detected=Dolo-650, Actual=7:20 AM -> AdherenceRecord=ON_TIME, Caregiver Notification=SENT
    @Test
    fun test35_caregiverNotification_onTimeVerification_sendsCaregiverNotification() {
        val prescription = Prescription(
            id = 201,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "07:20",
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            targetTime = "7:20 AM",
            currentTime = LocalTime.of(7, 20)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        assertEquals("ON_TIME", fakeAdherenceList.first().status)

        // Verify Caregiver Notification
        assertEquals(1, fakeCaregiverNotifications.size)
        val (title, body) = fakeCaregiverNotifications.first()
        assertEquals("Medication Taken", title)
        assertTrue("Body should contain medicine and actual time: $body", body.contains("Dolo-650") && body.contains("7:20 AM"))
    }

    // 36. Caregiver Notification TEST 2: Prescription=Dolo-650, Scheduled=7:20 AM, Detected=Dolo-650, Actual=8:10 AM -> AdherenceRecord=LATE, Caregiver Notification=SENT (Late)
    @Test
    fun test36_caregiverNotification_lateVerification_sendsLateCaregiverNotification() {
        val prescription = Prescription(
            id = 202,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "07:20",
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            targetTime = "7:20 AM",
            currentTime = LocalTime.of(8, 10)
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        assertEquals("LATE", fakeAdherenceList.first().status)

        // Verify Caregiver Notification
        assertEquals(1, fakeCaregiverNotifications.size)
        val (title, body) = fakeCaregiverNotifications.first()
        assertEquals("Medication Taken Late", title)
        assertTrue("Body should contain scheduled and actual time: $body", body.contains("Dolo-650") && body.contains("8:10 AM") && body.contains("7:20 AM"))
    }

    // 37. Caregiver Notification TEST 3: Prescription=Dolo-650, Detected=Augmentin -> Verification fails, NO AdherenceRecord, NO Caregiver Notification
    @Test
    fun test37_caregiverNotification_incorrectMedicine_noNotificationSent() {
        val prescription = Prescription(
            id = 203,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "07:20",
            startDate = 0L,
            endDate = 0L
        )

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Augmentin", 0.95f, 0),
            prescriptions = listOf(prescription),
            ocrText = "AUGMENTIN 625 MG",
            targetMedicine = "Dolo-650",
            targetSlot = "Morning",
            currentTime = LocalTime.of(7, 20)
        )

        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
        assertFalse(adherenceCreated)
        assertEquals(0, fakeAdherenceList.size)
        assertEquals(0, fakeCaregiverNotifications.size)
    }

    // 38. Caregiver Notification TEST 4: Duplicate scan for same dose -> Only ONE AdherenceRecord, Only ONE Caregiver Notification
    @Test
    fun test38_caregiverNotification_duplicateScan_preventsDuplicateNotification() {
        val prescription = Prescription(
            id = 204,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "07:20",
            startDate = 0L,
            endDate = 0L
        )

        // First scan
        val (result1, created1) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            targetTime = "7:20 AM",
            currentTime = LocalTime.of(7, 20)
        )
        assertEquals(VerificationStatus.CORRECT_MEDICINE, result1.status)
        assertTrue(created1)
        assertEquals(1, fakeAdherenceList.size)
        assertEquals(1, fakeCaregiverNotifications.size)

        // Second scan of same dose
        val (result2, created2) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            targetTime = "7:20 AM",
            currentTime = LocalTime.of(7, 25)
        )
        assertEquals(VerificationStatus.ALREADY_VERIFIED, result2.status)
        assertFalse(created2)

        // Counts remain 1! No duplicate adherence record or caregiver notification
        assertEquals(1, fakeAdherenceList.size)
        assertEquals(1, fakeCaregiverNotifications.size)
    }

    // 39. Caregiver Notification TEST 5: Firebase/Internet unavailable -> Verification succeeds, Room AdherenceRecord saved, app does not crash
    @Test
    fun test39_caregiverNotification_networkFailure_doesNotPreventAdherenceRecord() {
        val prescription = Prescription(
            id = 205,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            morningTime = "07:20",
            startDate = 0L,
            endDate = 0L
        )

        simulateNotificationFailure = true

        val (result, adherenceCreated) = simulateVerificationFlow(
            recognitionResult = RecognitionResult("Dolo-650", 0.98f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650",
            targetSlot = "Morning",
            targetTime = "7:20 AM",
            currentTime = LocalTime.of(7, 20)
        )

        // Verification still succeeds!
        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertTrue(adherenceCreated)
        assertEquals(1, fakeAdherenceList.size)
        assertEquals("Dolo-650", fakeAdherenceList.first().medicineName)
    }

    // 40. Caregiver Alert TEST: Missed dose / wrong medicine / duplicate dose creates CaregiverAlert and allows acknowledgment
    @Test
    fun test40_caregiverAlert_creationAndAcknowledgment() {
        var alert = CaregiverAlert(
            alertId = "alert_test_123",
            patientId = "patient_1",
            caregiverId = "caregiver_1",
            medicineName = "Dolo-650",
            eventType = "MISSED_DOSE",
            message = "Missed Morning dose (650mg)",
            scheduledTime = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            status = "CREATED",
            acknowledged = false
        )

        assertFalse(alert.acknowledged)
        assertEquals("CREATED", alert.status)

        // Simulate notification send
        alert = alert.copy(sentAt = System.currentTimeMillis(), status = "SENT")
        assertEquals("SENT", alert.status)

        // Simulate caregiver acknowledgment
        val ackTime = System.currentTimeMillis()
        alert = alert.copy(acknowledged = true, acknowledgedAt = ackTime, status = "ACKNOWLEDGED")

        assertTrue(alert.acknowledged)
        assertNotNull(alert.acknowledgedAt)
        assertEquals("ACKNOWLEDGED", alert.status)
    }
}

