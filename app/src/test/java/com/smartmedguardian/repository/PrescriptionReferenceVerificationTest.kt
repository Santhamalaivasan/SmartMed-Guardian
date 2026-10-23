package com.smartmedguardian.repository

import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.data.entity.MedicineReferenceImage
import com.smartmedguardian.ml.MatchStatus
import com.smartmedguardian.ml.ReferenceMatcherConfig
import com.smartmedguardian.ml.MedicineReferenceMatcher
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.util.MedicineVerificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PrescriptionReferenceVerificationTest {

    private lateinit var matcher: MedicineReferenceMatcher
    private lateinit var manager: MedicineVerificationManager

    @Before
    fun setUp() {
        matcher = MedicineReferenceMatcher()
        manager = MedicineVerificationManager()
    }

    @Test
    fun testMatchCorrectPrescriptionReference() {
        val prescription = Prescription(id = 1, medicineName = "Aspirin", dosage = "100mg", frequency = "Daily", morning = true, startDate = 0L, endDate = 0L)
        val refImages = listOf(
            MedicineReferenceImage(id = 1, prescriptionId = 1, imagePath = "path/to/ref1.jpg", embedding = "1.0,0.0,0.0")
        )
        val queryEmbedding = floatArrayOf(0.95f, 0.05f, 0.0f)

        val matchResult = matcher.findBestMatch(queryEmbedding, refImages, listOf(prescription), targetPrescriptionId = 1)
        assertEquals(MatchStatus.MATCH, matchResult.status)
        assertEquals(prescription.id, matchResult.prescription?.id)
        assertTrue(matchResult.similarity >= ReferenceMatcherConfig.MATCH_THRESHOLD)
    }

    @Test
    fun testScanDifferentMedicineMismatch() {
        val prescription = Prescription(id = 1, medicineName = "Aspirin", dosage = "100mg", frequency = "Daily", morning = true, startDate = 0L, endDate = 0L)
        val refImages = listOf(
            MedicineReferenceImage(id = 1, prescriptionId = 1, imagePath = "path/to/ref1.jpg", embedding = "1.0,0.0,0.0")
        )
        val queryEmbedding = floatArrayOf(0.0f, 1.0f, 0.0f)

        val matchResult = matcher.findBestMatch(queryEmbedding, refImages, listOf(prescription), targetPrescriptionId = 1)
        assertEquals(MatchStatus.MISMATCH, matchResult.status)
    }

    @Test
    fun testVisuallySimilarMedicinesPrescriptionIsolation() {
        val prescriptionA = Prescription(id = 1, medicineName = "Paracetamol A", dosage = "500mg", frequency = "Daily", morning = true, startDate = 0L, endDate = 0L)
        val prescriptionB = Prescription(id = 2, medicineName = "Paracetamol B", dosage = "500mg", frequency = "Daily", morning = true, startDate = 0L, endDate = 0L)
        val refImages = listOf(
            MedicineReferenceImage(id = 1, prescriptionId = 1, imagePath = "path/to/refA.jpg", embedding = "0.8,0.6,0.0"),
            MedicineReferenceImage(id = 2, prescriptionId = 2, imagePath = "path/to/refB.jpg", embedding = "0.79,0.61,0.0")
        )
        val queryEmbedding = floatArrayOf(0.79f, 0.61f, 0.0f)

        val matchResult = matcher.findBestMatch(queryEmbedding, refImages, listOf(prescriptionA, prescriptionB), targetPrescriptionId = 1)
        assertEquals(prescriptionA.id, matchResult.prescription?.id)
    }

    @Test
    fun testMultipleReferenceImagesAggregation() {
        val prescription = Prescription(id = 1, medicineName = "Ibuprofen", dosage = "400mg", frequency = "Daily", morning = true, startDate = 0L, endDate = 0L)
        val refImages = listOf(
            MedicineReferenceImage(id = 1, prescriptionId = 1, imagePath = "path/to/front.jpg", embedding = "0.5,0.5,0.0"),
            MedicineReferenceImage(id = 2, prescriptionId = 1, imagePath = "path/to/back.jpg", embedding = "0.95,0.1,0.0")
        )
        val queryEmbedding = floatArrayOf(0.96f, 0.08f, 0.0f)

        val matchResult = matcher.findBestMatch(queryEmbedding, refImages, listOf(prescription), targetPrescriptionId = 1)
        assertEquals(MatchStatus.MATCH, matchResult.status)
        assertEquals("path/to/back.jpg", matchResult.matchedImagePath)
    }

    @Test
    fun testUncertainResultBoundary() {
        val prescription = Prescription(id = 1, medicineName = "Aspirin", dosage = "100mg", frequency = "Daily", morning = true, startDate = 0L, endDate = 0L)
        val refImages = listOf(
            MedicineReferenceImage(id = 1, prescriptionId = 1, imagePath = "path/to/ref.jpg", embedding = "1.0,0.0,0.0")
        )
        val queryEmbedding = floatArrayOf(0.65f, 0.75f, 0.0f)

        val matchResult = matcher.findBestMatch(queryEmbedding, refImages, listOf(prescription), targetPrescriptionId = 1)
        assertEquals(MatchStatus.UNCERTAIN, matchResult.status)
    }

    @Test
    fun testLateScan345PMScannedAt455PM() {
        // Scheduled at 15:45 (3:45 PM), scanned at 16:55 (4:55 PM) -> 70 minute delay
        val prescription = Prescription(
            id = 10,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Afternoon",
            afternoon = true,
            afternoonTime = "15:45",
            startDate = 0L,
            endDate = 0L
        )

        val result = manager.verify(
            recognitionResult = RecognitionResult("Dolo-650", 0.9f, 0),
            prescriptions = listOf(prescription),
            targetMedicine = "Dolo-650",
            targetSlot = "Afternoon",
            targetTime = "15:45",
            currentTime = LocalTime.of(16, 55), // 4:55 PM
            currentDate = LocalDate.now(),
            prescriptionId = 10
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(10L, result.prescription?.id)
        assertEquals("Dolo-650", result.prescription?.medicineName)
        assertTrue(result.message?.contains("Afternoon dose") == true || result.message?.contains("verified successfully") == true)
    }

    @Test
    fun testMultiplePrescriptionsWithSimilarNamesPrescriptionIdIsolation() {
        val p1 = Prescription(id = 1, medicineName = "Vitamin C", dosage = "500mg", frequency = "Morning", morning = true, morningTime = "08:00", startDate = 0L, endDate = 0L)
        val p2 = Prescription(id = 2, medicineName = "Vitamin C", dosage = "1000mg", frequency = "Afternoon", afternoon = true, afternoonTime = "14:00", startDate = 0L, endDate = 0L)

        val result = manager.verify(
            recognitionResult = RecognitionResult("Vitamin C", 0.95f, 0),
            prescriptions = listOf(p1, p2),
            targetMedicine = "Vitamin C",
            targetSlot = "Afternoon",
            targetTime = "14:00",
            currentTime = LocalTime.of(14, 10),
            currentDate = LocalDate.now(),
            prescriptionId = 2
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(2L, result.prescription?.id)
        assertEquals("1000mg", result.prescription?.dosage)
    }
}
