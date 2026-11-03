package com.smartmedguardian.repository

import android.content.Context
import android.app.NotificationManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.smartmedguardian.data.dao.AdherenceDao
import com.smartmedguardian.data.dao.CaregiverAlertDao
import com.smartmedguardian.data.dao.MedicineConditionDao
import com.smartmedguardian.data.dao.MedicineHistoryDao
import com.smartmedguardian.data.dao.MedicineReferenceImageDao
import com.smartmedguardian.data.dao.PrescriptionDao
import com.smartmedguardian.data.dao.SafetyDao
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.CaregiverAlert
import com.smartmedguardian.data.entity.CaregiverInfo
import com.smartmedguardian.data.entity.DrugInteraction
import com.smartmedguardian.data.entity.MedicineHistory
import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.data.entity.MedicineReferenceImage
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.data.entity.UserAllergy
import com.smartmedguardian.ml.MedicineClassifier
import com.smartmedguardian.ml.MedicineEmbeddingHelper
import com.smartmedguardian.ml.MedicineReferenceMatcher
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.ml.ReferenceMatchResult
import com.smartmedguardian.model.RiskAssessment
import com.smartmedguardian.model.VerificationResult
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.util.CaregiverNotificationHelper
import com.smartmedguardian.util.CaregiverSmsHelper
import com.smartmedguardian.util.MedicineVerificationManager
import com.smartmedguardian.util.SecurityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

interface MedicineRepository {
    suspend fun classifyImage(bitmap: Bitmap): Result<RecognitionResult>
    
    // Prescription methods
    fun getAllPrescriptions(): Flow<List<Prescription>>
    suspend fun insertPrescription(prescription: Prescription): Long
    suspend fun updatePrescription(prescription: Prescription)
    suspend fun deletePrescription(prescription: Prescription)
    suspend fun getPrescriptionByName(name: String): Prescription?

    // Reference Images & Visual Embeddings
    fun getReferenceImagesForPrescription(prescriptionId: Long): Flow<List<MedicineReferenceImage>>
    suspend fun insertPrescriptionWithReferenceImages(prescription: Prescription, imageUris: List<Uri>): Long
    suspend fun updatePrescriptionWithReferenceImages(prescription: Prescription, newImageUris: List<Uri>)
    suspend fun matchReferenceImages(queryEmbedding: FloatArray, prescriptionId: Long? = null): ReferenceMatchResult
    
    // History methods
    fun getAllHistory(): Flow<List<MedicineHistory>>
    suspend fun insertHistory(history: MedicineHistory)
    suspend fun clearHistory()
    suspend fun clearLocalUserData()
    
    // Verification logic
    suspend fun verifyRecognition(
        recognitionResult: RecognitionResult,
        ocrText: String? = null,
        queryBitmap: Bitmap? = null,
        targetMedicine: String? = null,
        targetSlot: String? = null,
        targetTime: String? = null,
        prescriptionId: Long? = null
    ): VerificationResult

    // Medicine Info & Safety
    suspend fun getMedicineInfo(name: String): MedicineInfo?
    suspend fun getMedicineByBarcode(barcode: String): MedicineInfo?
    fun searchMedicines(query: String): Flow<List<MedicineInfo>>
    suspend fun checkInteraction(medA: String, medB: String): DrugInteraction?
    
    // Allergy methods
    fun getAllAllergies(): Flow<List<UserAllergy>>
    suspend fun insertAllergy(allergy: UserAllergy)
    suspend fun deleteAllergy(allergy: UserAllergy)

    // Adherence methods
    fun getAllAdherenceRecords(): Flow<List<AdherenceRecord>>
    suspend fun insertAdherenceRecord(record: AdherenceRecord)
    fun getAdherenceSince(startTime: Long): Flow<List<AdherenceRecord>>
    suspend fun getAdherenceForMedicineInWindow(name: String, startTime: Long, endTime: Long): List<AdherenceRecord>
    suspend fun getAdherenceForCanonicalNamesInWindow(names: List<String>, startTime: Long, endTime: Long): List<AdherenceRecord>
    suspend fun getRecordByDoseId(doseId: String): AdherenceRecord?
    suspend fun markDoseMissed(
        scheduledDoseId: String,
        medicineName: String,
        scheduledTime: Long,
        dosage: String,
        slotName: String
    ): AdherenceRecord
    
    // Caregiver methods
    fun getCaregiver(): Flow<CaregiverInfo?>
    suspend fun saveCaregiver(info: CaregiverInfo)
    suspend fun deleteCaregiver()

    // Caregiver Alerts
    suspend fun createCaregiverAlert(
        eventType: String,
        medicineName: String,
        message: String,
        scheduledTime: Long = System.currentTimeMillis()
    ): CaregiverAlert

    suspend fun acknowledgeCaregiverAlert(alertId: String): Boolean

    fun getLocalCaregiverAlerts(): Flow<List<CaregiverAlert>>
    fun getRemoteCaregiverAlerts(patientId: String): Flow<List<CaregiverAlert>>

    // Remote Monitoring
    fun getRemotePrescriptions(patientId: String): Flow<List<Prescription>>
    fun getRemoteAdherence(patientId: String): Flow<List<AdherenceRecord>>
    fun getRemoteRisk(patientId: String): Flow<RiskAssessment?>
    suspend fun uploadRisk(risk: RiskAssessment)

    // Condition Trend Analysis methods
    suspend fun getMedicinesForCondition(conditionName: String): List<String>
    suspend fun getConditionPrescriptions(conditionName: String): List<Prescription>
    suspend fun getConditionAdherenceRecords(conditionName: String, startTime: Long, endTime: Long): List<AdherenceRecord>

    // Cloud Sync
    suspend fun syncLocalToCloud()
}

class MedicineRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classifier: MedicineClassifier,
    private val embeddingHelper: MedicineEmbeddingHelper,
    private val referenceMatcher: MedicineReferenceMatcher,
    private val prescriptionDao: PrescriptionDao,
    private val medicineReferenceImageDao: MedicineReferenceImageDao,
    private val historyDao: MedicineHistoryDao,
    private val safetyDao: SafetyDao,
    private val adherenceDao: AdherenceDao,
    private val caregiverAlertDao: CaregiverAlertDao,
    private val medicineConditionDao: MedicineConditionDao,
    private val verificationManager: MedicineVerificationManager,
    private val securityHelper: SecurityHelper,
    private val smsHelper: CaregiverSmsHelper,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : MedicineRepository {
    override suspend fun classifyImage(bitmap: Bitmap): Result<RecognitionResult> = withContext(Dispatchers.Default) {
        try {
            val result = classifier.classify(bitmap)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAllPrescriptions(): Flow<List<Prescription>> = prescriptionDao.getAllPrescriptions()

    override suspend fun insertPrescription(prescription: Prescription): Long {
        return insertPrescriptionWithReferenceImages(prescription, emptyList())
    }

    override suspend fun updatePrescription(prescription: Prescription) {
        updatePrescriptionWithReferenceImages(prescription, emptyList())
    }

    override fun getReferenceImagesForPrescription(prescriptionId: Long): Flow<List<MedicineReferenceImage>> {
        return medicineReferenceImageDao.getImagesForPrescription(prescriptionId)
    }

    override suspend fun insertPrescriptionWithReferenceImages(
        prescription: Prescription,
        imageUris: List<Uri>
    ): Long = withContext(Dispatchers.IO) {
        val id = prescriptionDao.insertPrescription(prescription)
        val savedPrescription = prescription.copy(id = id)

        val savedPaths = saveReferenceImages(id, imageUris)
        if (savedPaths.isNotEmpty()) {
            val refEntities = savedPaths.map { path ->
                val emb = try {
                    embeddingHelper.extractEmbeddingFromFile(path)
                } catch (e: Exception) {
                    Log.e("MedicineRepository", "Error extracting embedding for $path", e)
                    null
                }
                val embStr = emb?.let { MedicineEmbeddingHelper.floatArrayToString(it) }
                MedicineReferenceImage(
                    prescriptionId = id,
                    imagePath = path,
                    embedding = embStr
                )
            }
            medicineReferenceImageDao.insertReferenceImages(refEntities)
        }

        uploadPrescription(savedPrescription)
        id
    }

    override suspend fun updatePrescriptionWithReferenceImages(
        prescription: Prescription,
        newImageUris: List<Uri>
    ) = withContext(Dispatchers.IO) {
        prescriptionDao.updatePrescription(prescription)

        if (newImageUris.isNotEmpty()) {
            val savedPaths = saveReferenceImages(prescription.id, newImageUris)
            if (savedPaths.isNotEmpty()) {
                val refEntities = savedPaths.map { path ->
                    val emb = try {
                        embeddingHelper.extractEmbeddingFromFile(path)
                    } catch (e: Exception) {
                        Log.e("MedicineRepository", "Error extracting embedding for $path", e)
                        null
                    }
                    val embStr = emb?.let { MedicineEmbeddingHelper.floatArrayToString(it) }
                    MedicineReferenceImage(
                        prescriptionId = prescription.id,
                        imagePath = path,
                        embedding = embStr
                    )
                }
                medicineReferenceImageDao.insertReferenceImages(refEntities)
            }
        }

        uploadPrescription(prescription)
    }

    override suspend fun matchReferenceImages(queryEmbedding: FloatArray, prescriptionId: Long?): ReferenceMatchResult = withContext(Dispatchers.IO) {
        val rawRefImages = if (prescriptionId != null && prescriptionId > 0L) {
            medicineReferenceImageDao.getImagesForPrescriptionOnce(prescriptionId)
        } else {
            medicineReferenceImageDao.getAllReferenceImagesOnce()
        }
        val allPrescriptions = prescriptionDao.getAllPrescriptions().first()

        // Compute and persist embeddings lazily if missing
        val validRefImages = rawRefImages.map { ref ->
            if (ref.embedding.isNull_or_blank()) {
                val emb = embeddingHelper.extractEmbeddingFromFile(ref.imagePath)
                if (emb != null) {
                    val embStr = MedicineEmbeddingHelper.floatArrayToString(emb)
                    val updated = ref.copy(embedding = embStr)
                    try {
                        medicineReferenceImageDao.updateReferenceImage(updated)
                    } catch (e: Exception) {
                        Log.e("MedicineRepository", "Error updating reference embedding", e)
                    }
                    updated
                } else ref
            } else ref
        }

        referenceMatcher.findBestMatch(queryEmbedding, validRefImages, allPrescriptions, prescriptionId)
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.isBlank()
    }

    private fun saveReferenceImages(prescriptionId: Long, imageUris: List<Uri>): List<String> {
        if (imageUris.isEmpty()) return emptyList()

        val dir = File(context.filesDir, "medicine_references/$prescriptionId")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val savedPaths = mutableListOf<String>()
        imageUris.forEachIndexed { index, uri ->
            try {
                val file = File(dir, "reference_${index + 1}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (file.exists() && file.length() > 0) {
                    savedPaths.add(file.absolutePath)
                }
            } catch (e: Exception) {
                Log.e("MedicineRepository", "Error saving reference image from $uri", e)
            }
        }
        return savedPaths
    }

    private suspend fun uploadPrescription(prescription: Prescription) {
        try {
            val userId = auth.currentUser?.uid ?: return
            firestore.collection("users").document(userId)
                .collection("prescriptions").document(prescription.id.toString())
                .set(prescription).await()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Failed to upload prescription to Firestore", e)
        }
    }

    override suspend fun deletePrescription(prescription: Prescription) = withContext(Dispatchers.IO) {
        prescriptionDao.deletePrescription(prescription)

        try {
            val dir = File(context.filesDir, "medicine_references/${prescription.id}")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Error deleting reference image directory for prescription ${prescription.id}", e)
        }

        try {
            val userId = auth.currentUser?.uid ?: return@withContext
            firestore.collection("users").document(userId)
                .collection("prescriptions").document(prescription.id.toString())
                .delete().await()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Failed to delete prescription from Firestore", e)
        }
    }

    override suspend fun getPrescriptionByName(name: String): Prescription? {
        return prescriptionDao.getPrescriptionByName(name)
    }

    override fun getAllHistory(): Flow<List<MedicineHistory>> = historyDao.getAllHistory()

    override suspend fun insertHistory(history: MedicineHistory) {
        val userId = auth.currentUser?.uid
        val historyWithUser = history.copy(userId = userId)
        val id = historyDao.insertHistory(historyWithUser)
        CoroutineScope(Dispatchers.IO).launch {
            uploadHistory(historyWithUser.copy(id = id))
        }
    }

    private suspend fun uploadHistory(history: MedicineHistory) {
        try {
            val userId = auth.currentUser?.uid ?: return
            firestore.collection("users").document(userId)
                .collection("history").document(history.id.toString())
                .set(history).await()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Failed to upload history to Firestore: ${e.message}", e)
        }
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    override suspend fun clearLocalUserData(): Unit = withContext(Dispatchers.IO) {
        // 1. Cancel scheduled alarms for active prescriptions
        try {
            val prescriptions = prescriptionDao.getAllPrescriptions().first()
            val reminderScheduler = com.smartmedguardian.worker.ReminderScheduler(context)
            for (p in prescriptions) {
                reminderScheduler.cancelAllReminders(p.id)
            }
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Error cancelling reminders during clearLocalUserData", e)
        }

        // 2. Cancel WorkManager jobs
        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Error cancelling WorkManager jobs during clearLocalUserData", e)
        }

        // 3. Clear active notifications
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Error clearing system notifications during clearLocalUserData", e)
        }

        // 4. Clear all user-specific Room tables
        historyDao.clearHistory()
        prescriptionDao.deleteAllPrescriptions()
        medicineReferenceImageDao.deleteAllReferenceImages()
        safetyDao.deleteAllAllergies()
        adherenceDao.deleteAllAdherenceRecords()
        adherenceDao.deleteCaregiver()
        caregiverAlertDao.deleteAllCaregiverAlerts()

        // 5. Delete disk reference image directory
        val refImagesDir = File(context.filesDir, "medicine_references")
        if (refImagesDir.exists()) {
            val deleted = refImagesDir.deleteRecursively()
            if (!deleted) {
                Log.w("MedicineRepository", "Failed to delete reference images directory")
            }
        }

        // 6. Clear cache directory
        try {
            context.cacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Error clearing cache directory", e)
        }
    }

    override suspend fun verifyRecognition(
        recognitionResult: RecognitionResult,
        ocrText: String?,
        queryBitmap: Bitmap?,
        targetMedicine: String?,
        targetSlot: String?,
        targetTime: String?,
        prescriptionId: Long?
    ): VerificationResult = withContext(Dispatchers.IO) {
        val dbQueryStart = System.currentTimeMillis()
        Log.d("SMARTMED_ANALYSIS", "[DATABASE_QUERY_START] Fetching active prescriptions and adherence records from Room DB")

        val prescriptions = try {
            prescriptionDao.getAllPrescriptions().first()
        } catch (e: Exception) {
            Log.e("SMARTMED_ANALYSIS", "Error querying prescriptions from Room DB: ${e.message}", e)
            emptyList()
        }

        val currentTime = LocalTime.now()
        val currentDate = LocalDate.now()

        // Sanitize target parameters
        val cleanTargetMedicine = targetMedicine?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        val cleanTargetSlot = targetSlot?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        val cleanTargetTime = targetTime?.takeIf { it.isNotBlank() && !it.startsWith("{") }

        val referenceMatchResult = if (queryBitmap != null && embeddingHelper.isModelLoaded()) {
            try {
                val embStart = System.currentTimeMillis()
                val scaled224 = if (queryBitmap.width == 224 && queryBitmap.height == 224) {
                    queryBitmap
                } else {
                    Bitmap.createScaledBitmap(queryBitmap, 224, 224, true)
                }
                val queryEmbedding = embeddingHelper.extractEmbedding(scaled224)
                val embTime = System.currentTimeMillis() - embStart
                Log.d("MedicineAnalysis", "[MedicineAnalysis] Reference embedding: ${embTime} ms")

                if (scaled224 !== queryBitmap && !scaled224.isRecycled) {
                    scaled224.recycle()
                }

                val matchStart = System.currentTimeMillis()
                val matchRes = matchReferenceImages(queryEmbedding, prescriptionId)
                val matchTime = System.currentTimeMillis() - matchStart
                Log.d("MedicineAnalysis", "[MedicineAnalysis] Reference matching: ${matchTime} ms")
                matchRes
            } catch (e: Exception) {
                Log.e("MedicineRepository", "Error running reference embedding match", e)
                null
            }
        } else null

        // Find candidate prescription from Room DB (source of truth)
        val candidatePrescription = if (prescriptionId != null && prescriptionId > 0L) {
            prescriptions.find { it.id == prescriptionId }
                ?: cleanTargetMedicine?.let { target ->
                    prescriptions.find {
                        val pNorm = verificationManager.normalizeTextForMatching(it.medicineName)
                        val tNorm = verificationManager.normalizeTextForMatching(target)
                        pNorm.isNotBlank() && (pNorm == tNorm || pNorm.contains(tNorm) || tNorm.contains(pNorm))
                    }
                }
        } else if (!cleanTargetMedicine.isNullOrBlank()) {
            prescriptions.find {
                val pNorm = verificationManager.normalizeTextForMatching(it.medicineName)
                val tNorm = verificationManager.normalizeTextForMatching(cleanTargetMedicine)
                pNorm.isNotBlank() && (pNorm == tNorm || pNorm.contains(tNorm) || tNorm.contains(pNorm))
            }
        } else {
            val ocrMatch = verificationManager.matchSupportedMedicineFromOcr(ocrText ?: "")
            val recognizedName = ocrMatch?.canonicalName ?: recognitionResult.name
            prescriptions.find { 
                val pNorm = verificationManager.normalizeTextForMatching(it.medicineName)
                val rNorm = verificationManager.normalizeTextForMatching(recognizedName)
                pNorm.isNotBlank() && (pNorm == rNorm || pNorm.contains(rNorm) || rNorm.contains(pNorm))
            }
        }

        val detectedMedName = ocrText?.let { verificationManager.matchSupportedMedicineFromOcr(it)?.canonicalName }
            ?: recognitionResult.name

        // Detailed logging as required by Prompt Requirement 14 & 15
        if (candidatePrescription != null) {
            val normPrescription = verificationManager.normalizeTextForMatching(candidatePrescription.medicineName)
            val normDetected = verificationManager.normalizeTextForMatching(detectedMedName)
            val isMedMatch = normPrescription == normDetected || normPrescription.contains(normDetected) || normDetected.contains(normPrescription)

            val scheduledTimeDisplay = verificationManager.getFormattedScheduledTime(
                prescription = candidatePrescription,
                slotName = cleanTargetSlot ?: verificationManager.inferSlotFromTime(currentTime, candidatePrescription),
                explicitTimeStr = cleanTargetTime
            )

            val (scheduledHour, scheduledMin) = verificationManager.parseHourMinute(
                cleanTargetTime ?: candidatePrescription.morningTime, 8
            )
            val scheduledMins = scheduledHour * 60 + scheduledMin
            val currentMins = currentTime.hour * 60 + currentTime.minute
            var diffMins = Math.abs(currentMins - scheduledMins)
            if (diffMins > 720) diffMins = 1440 - diffMins

            Log.d("PrescriptionDebug", "Prescription ID = ${candidatePrescription.id}")
            Log.d("PrescriptionDebug", "Prescription medicine = [REDACTED_MEDICINE]")
            Log.d("PrescriptionDebug", "Detected medicine = [REDACTED_MEDICINE]")
            Log.d("PrescriptionDebug", "Normalized prescription = [REDACTED_MEDICINE]")
            Log.d("PrescriptionDebug", "Normalized detected = [REDACTED_MEDICINE]")
            Log.d("PrescriptionDebug", "Scheduled time = $scheduledTimeDisplay")
            Log.d("PrescriptionDebug", "Actual time = $currentTime")
            Log.d("PrescriptionDebug", "Time difference = $diffMins mins")
            Log.d("PrescriptionDebug", "Medicine match = $isMedMatch")
        } else {
            Log.d("PrescriptionDebug", "Prescription lookup failed")
        }

        // Target-slot priority:
        // 1. Explicit cleanTargetSlot
        // 2. Infer slot from currentTime and prescription
        val activeSlot = cleanTargetSlot?.takeIf { it.isNotBlank() }
            ?: verificationManager.inferSlotFromTime(currentTime, candidatePrescription)

        // Check if dose was already verified today for this slot
        val alreadyVerified = if (candidatePrescription != null) {
            val (wStart, wEnd) = verificationManager.getSlotTimeWindowMillis(activeSlot, currentTime, currentDate)
            try {
                val existingRecords = adherenceDao.getAdherenceForMedicineInWindow(candidatePrescription.medicineName, wStart, wEnd)
                existingRecords.any { it.status == "TAKEN" || it.status == "ON_TIME" || it.status == "LATE" }
            } catch (e: Exception) {
                Log.e("SMARTMED_ANALYSIS", "Error querying adherence window from Room DB: ${e.message}", e)
                false
            }
        } else false

        val dbQueryElapsed = System.currentTimeMillis() - dbQueryStart
        Log.d("SMARTMED_ANALYSIS", "[DATABASE_QUERY_COMPLETE] Room DB queries completed in ${dbQueryElapsed}ms. Prescriptions count=${prescriptions.size}, AlreadyVerified=$alreadyVerified")

        val verification = verificationManager.verify(
            recognitionResult = recognitionResult,
            prescriptions = prescriptions,
            ocrText = ocrText,
            referenceMatchResult = referenceMatchResult,
            targetMedicine = cleanTargetMedicine,
            targetSlot = activeSlot,
            targetTime = cleanTargetTime,
            currentTime = currentTime,
            currentDate = currentDate,
            alreadyVerified = alreadyVerified,
            prescriptionId = prescriptionId
        )

        // Post-verification atomic duplicate and active ingredient evaluation
        val finalVerification = if (verification.status == VerificationStatus.CORRECT_MEDICINE && verification.prescription != null) {
            val verifiedPrescription = verification.prescription
            val (wStart, wEnd) = verificationManager.getSlotTimeWindowMillis(activeSlot, currentTime, currentDate)
            
            val canonicalTarget = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(verifiedPrescription.medicineName)
            val namesToCheck = listOf(verifiedPrescription.medicineName, canonicalTarget, canonicalTarget.replace("-", ""), canonicalTarget.replace("-", " "))
            val existingRecords = try {
                adherenceDao.getAdherenceForCanonicalNamesInWindow(namesToCheck, wStart, wEnd)
            } catch (e: Exception) {
                emptyList()
            }

            val isDuplicateDose = existingRecords.any { it.status == "TAKEN" || it.status == "ON_TIME" || it.status == "LATE" }

            if (isDuplicateDose) {
                verification.copy(
                    status = VerificationStatus.ALREADY_VERIFIED,
                    message = "⚠️ Dose Already Verified\nThe $activeSlot dose of ${verifiedPrescription.medicineName} has already been verified for today.\nDose was not marked as taken again."
                )
            } else {
                val verifiedInfo = getMedicineInfo(verifiedPrescription.medicineName)
                val primaryIngredient = verifiedInfo?.genericName?.split("(")?.first()?.trim() ?: ""

                var duplicateIngredientBrand: String? = null
                if (primaryIngredient.isNotBlank()) {
                    try {
                        val windowRecords = adherenceDao.getAdherenceSince(wStart).first()
                        for (rec in windowRecords) {
                            if (rec.status == "TAKEN" || rec.status == "ON_TIME" || rec.status == "LATE") {
                                val recCanonical = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(rec.medicineName)
                                if (!com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(recCanonical, canonicalTarget)) {
                                    val recInfo = getMedicineInfo(rec.medicineName)
                                    if (recInfo != null && recInfo.genericName.contains(primaryIngredient, ignoreCase = true)) {
                                        duplicateIngredientBrand = rec.medicineName
                                        break
                                    }
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                if (duplicateIngredientBrand != null) {
                    verification.copy(
                        status = VerificationStatus.DUPLICATE_INGREDIENT,
                        message = "⚠️ Duplicate Active Ingredient Warning\nAnother medication ($duplicateIngredientBrand) containing the same active ingredient ($primaryIngredient) has already been recorded for this dose window.\nPlease consult your physician before taking this dose."
                    )
                } else {
                    verification
                }
            }
        } else {
            verification
        }

        // Synchronized database persistence based on final verification status
        if (finalVerification.status == VerificationStatus.CORRECT_MEDICINE && finalVerification.prescription != null) {
            val verifiedPrescription = finalVerification.prescription
            val scheduledMillis = verificationManager.getScheduledTimeMillis(verifiedPrescription, activeSlot, cleanTargetTime, currentTime, currentDate)
            val actualMillis = System.currentTimeMillis()

            val delayMinutes = maxOf(0, ((actualMillis - scheduledMillis) / 60000L).toInt())
            val adherenceStatus = if (delayMinutes > 30) "LATE" else "ON_TIME"

            val record = AdherenceRecord(
                medicineName = verifiedPrescription.medicineName,
                scheduledTime = scheduledMillis,
                actualTime = actualMillis,
                status = adherenceStatus,
                delayMinutes = delayMinutes
            )

            val (wStart, wEnd) = verificationManager.getSlotTimeWindowMillis(activeSlot, currentTime, currentDate)
            val canonicalTarget = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(verifiedPrescription.medicineName)
            val namesToCheck = listOf(verifiedPrescription.medicineName, canonicalTarget)
            val doubleCheck = try {
                adherenceDao.getAdherenceForCanonicalNamesInWindow(namesToCheck, wStart, wEnd)
            } catch (e: Exception) { emptyList() }

            val existingMissed = doubleCheck.firstOrNull { it.status == "MISSED" }
            if (existingMissed != null) {
                val updatedRecord = existingMissed.copy(
                    actualTime = actualMillis,
                    status = "LATE",
                    delayMinutes = delayMinutes
                )
                adherenceDao.updateAdherenceRecord(updatedRecord)
                uploadAdherence(updatedRecord)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        notifyCaregiverMedicationTaken(
                            record = updatedRecord,
                            prescription = verifiedPrescription,
                            slotName = activeSlot,
                            explicitTimeStr = cleanTargetTime
                        )
                    } catch (e: Exception) {
                        Log.e("CaregiverNotification", "Notification failed: ${e.message}", e)
                    }
                }
            } else if (!doubleCheck.any { it.status == "TAKEN" || it.status == "ON_TIME" || it.status == "LATE" }) {
                insertAdherenceRecord(record)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        notifyCaregiverMedicationTaken(
                            record = record,
                            prescription = verifiedPrescription,
                            slotName = activeSlot,
                            explicitTimeStr = cleanTargetTime
                        )
                    } catch (e: Exception) {
                        Log.e("CaregiverNotification", "Notification failed: ${e.message}", e)
                    }
                }
            }

            val history = MedicineHistory(
                detectedMedicineName = verifiedPrescription.medicineName,
                confidence = finalVerification.recognitionResult.confidence,
                timestamp = System.currentTimeMillis(),
                verificationStatus = VerificationStatus.CORRECT_MEDICINE.name,
                prescriptionId = verifiedPrescription.id
            )
            insertHistory(history)
        } else {
            val historyName = finalVerification.prescription?.medicineName 
                ?: finalVerification.recognitionResult.name.ifBlank { "Unknown" }

            val history = MedicineHistory(
                detectedMedicineName = historyName,
                confidence = finalVerification.recognitionResult.confidence,
                timestamp = System.currentTimeMillis(),
                verificationStatus = finalVerification.status.name,
                prescriptionId = finalVerification.prescription?.id
            )
            insertHistory(history)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (finalVerification.status) {
                        VerificationStatus.ALREADY_VERIFIED, VerificationStatus.DUPLICATE_INGREDIENT -> {
                            createCaregiverAlert(
                                eventType = "DUPLICATE_DOSE",
                                medicineName = historyName,
                                message = finalVerification.message ?: "Duplicate dose attempt detected for $historyName"
                            )
                        }
                        VerificationStatus.UNSCHEDULED_TIME -> {
                            createCaregiverAlert(
                                eventType = "WRONG_MEDICINE",
                                medicineName = historyName,
                                message = "Unscheduled dose attempt detected for $historyName ($activeSlot slot)"
                            )
                        }
                        VerificationStatus.MEDICINE_NOT_FOUND -> {
                            createCaregiverAlert(
                                eventType = "WRONG_MEDICINE",
                                medicineName = historyName,
                                message = "Scanned medicine ($historyName) is not in prescribed active list"
                            )
                        }
                        VerificationStatus.UNKNOWN_MEDICINE, VerificationStatus.UNABLE_TO_VERIFY -> {
                            createCaregiverAlert(
                                eventType = "SAFETY_WARNING",
                                medicineName = historyName,
                                message = "Unable to verify medicine safety: ${finalVerification.message}"
                            )
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e("SMARTMED_CAREGIVER", "Error generating caregiver alert on verification failure: ${e.message}", e)
                }
            }
        }

        finalVerification
    }

    private suspend fun notifyCaregiverMedicationTaken(
        record: AdherenceRecord,
        prescription: Prescription,
        slotName: String,
        explicitTimeStr: String? = null
    ) {
        val caregiver = try {
            adherenceDao.getCaregiver().first()
        } catch (e: Exception) {
            null
        }

        val (title, body) = CaregiverNotificationHelper.buildNotificationPayload(
            record = record,
            explicitTimeStr = explicitTimeStr
        )

        // Attempt automatic SMS alert if caregiver exists and phone number is available
        if (caregiver != null && caregiver.encryptedPhoneNumber.isNotBlank()) {
            try {
                val decryptedPhone = securityHelper.decrypt(caregiver.encryptedPhoneNumber)
                if (decryptedPhone.isNotBlank()) {
                    val smsText = "SmartMed Guardian: $body"
                    val eventId = if (record.id != 0L) "adh_${record.id}" else "adh_${record.medicineName}_${record.scheduledTime}"
                    val smsResult = smsHelper.sendSms(
                        eventId = eventId,
                        phoneNumber = decryptedPhone,
                        message = smsText
                    )
                    Log.d("SMARTMED_CAREGIVER", "Caregiver SMS dispatch result: $smsResult")
                }
            } catch (e: Exception) {
                Log.e("SMARTMED_CAREGIVER", "Error decrypting phone number or sending SMS: ${e.message}", e)
            }
        }

        val token = caregiver?.caregiverFcmToken
        if (!token.isNullOrBlank()) {
            Log.d("SMARTMED_CAREGIVER", "Caregiver FCM token found")
        } else {
            Log.d("SMARTMED_CAREGIVER", "Caregiver FCM token missing")
        }

        try {
            val userId = auth.currentUser?.uid
            if (userId.isNullOrBlank()) {
                Log.w("SMARTMED_CAREGIVER", "Patient user not authenticated for caregiver notification")
                return
            }

            Log.d("SMARTMED_CAREGIVER", "Medication taken notification created")

            val notificationId = System.currentTimeMillis().toString()
            val notificationData = mapOf(
                "title" to title,
                "body" to body,
                "medicineName" to record.medicineName,
                "status" to record.status,
                "scheduledTime" to record.scheduledTime,
                "actualTime" to record.actualTime,
                "patientId" to userId,
                "timestamp" to System.currentTimeMillis()
            )

            Log.d("SMARTMED_CAREGIVER", "Sending FCM notification")

            // Persist to Firestore for remote caregiver sync / Cloud Functions trigger
            firestore.collection("users").document(userId)
                .collection("caregiver_notifications")
                .document(notificationId)
                .set(notificationData).await()

            Log.d("SMARTMED_CAREGIVER", "FCM notification sent successfully")

            // Display notification locally on current device
            CaregiverNotificationHelper.showNotification(context, title, body, userId)
        } catch (e: Exception) {
            Log.e("SMARTMED_CAREGIVER", "Notification failed: ${e.message}", e)
            // Local Room AdherenceRecord remains intact!
        }
    }

    override suspend fun getMedicineInfo(name: String): MedicineInfo? {
        val canonicalName = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(name)
        return safetyDao.getMedicineInfoByName(canonicalName) ?: safetyDao.getMedicineInfoByName(name)
    }

    override suspend fun getMedicineByBarcode(barcode: String): MedicineInfo? {
        return safetyDao.getMedicineInfoByBarcode(barcode)
    }

    override fun searchMedicines(query: String): Flow<List<MedicineInfo>> {
        return safetyDao.searchMedicines(query)
    }

    override suspend fun checkInteraction(medA: String, medB: String): DrugInteraction? {
        return safetyDao.getInteraction(medA, medB)
    }

    override fun getAllAllergies(): Flow<List<UserAllergy>> = safetyDao.getAllAllergies()

    override suspend fun insertAllergy(allergy: UserAllergy) {
        safetyDao.insertAllergy(allergy)
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .collection("allergies").document(allergy.allergen)
            .set(allergy).await()
    }

    override suspend fun deleteAllergy(allergy: UserAllergy) {
        safetyDao.deleteAllergy(allergy)
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .collection("allergies").document(allergy.allergen)
            .delete().await()
    }

    override fun getAllAdherenceRecords(): Flow<List<AdherenceRecord>> = adherenceDao.getAllAdherenceRecords()

    override suspend fun insertAdherenceRecord(record: AdherenceRecord) {
        Log.d("AdherenceDebug", "Adherence record inserted: status=${record.status}, scheduledTime=${record.scheduledTime}, actualTime=${record.actualTime}, delayMinutes=${record.delayMinutes}")
        adherenceDao.insertAdherenceRecord(record)
        CoroutineScope(Dispatchers.IO).launch {
            uploadAdherence(record)
        }
    }

    private suspend fun uploadAdherence(record: AdherenceRecord) {
        try {
            val userId = auth.currentUser?.uid ?: return
            firestore.collection("users").document(userId)
                .collection("adherence").document(record.id.toString())
                .set(record).await()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Failed to upload adherence to Firestore: ${e.message}", e)
        }
    }

    override fun getAdherenceSince(startTime: Long): Flow<List<AdherenceRecord>> = adherenceDao.getAdherenceSince(startTime)

    override suspend fun getAdherenceForMedicineInWindow(name: String, startTime: Long, endTime: Long): List<AdherenceRecord> {
        return adherenceDao.getAdherenceForMedicineInWindow(name, startTime, endTime)
    }

    override suspend fun getAdherenceForCanonicalNamesInWindow(names: List<String>, startTime: Long, endTime: Long): List<AdherenceRecord> {
        return adherenceDao.getAdherenceForCanonicalNamesInWindow(names, startTime, endTime)
    }

    override suspend fun getRecordByDoseId(doseId: String): AdherenceRecord? {
        return adherenceDao.getRecordByDoseId(doseId)
    }

    override suspend fun markDoseMissed(
        scheduledDoseId: String,
        medicineName: String,
        scheduledTime: Long,
        dosage: String,
        slotName: String
    ): AdherenceRecord = withContext(Dispatchers.IO) {
        val existing = adherenceDao.getRecordByDoseId(scheduledDoseId)
        if (existing != null) {
            if (existing.status == "MISSED" && existing.caregiverNotified) {
                return@withContext existing
            }
            val updated = existing.copy(
                status = "MISSED",
                caregiverNotified = true,
                notificationStatus = "SENT",
                notificationTimestamp = System.currentTimeMillis()
            )
            adherenceDao.updateAdherenceRecord(updated)
            uploadAdherence(updated)
            return@withContext updated
        }

        val record = AdherenceRecord(
            medicineName = medicineName,
            scheduledTime = scheduledTime,
            actualTime = null,
            status = "MISSED",
            scheduledDoseId = scheduledDoseId,
            caregiverNotified = true,
            notificationStatus = "SENT",
            notificationTimestamp = System.currentTimeMillis()
        )
        adherenceDao.insertAdherenceRecord(record)
        uploadAdherence(record)

        try {
            createCaregiverAlert(
                eventType = "MISSED_DOSE",
                medicineName = medicineName,
                message = "Missed $slotName dose (${dosage.ifBlank { "as prescribed" }})",
                scheduledTime = scheduledTime
            )
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Failed to create caregiver alert for missed dose: ${e.message}", e)
        }

        record
    }

    override fun getCaregiver(): Flow<CaregiverInfo?> = adherenceDao.getCaregiver()

    override suspend fun saveCaregiver(info: CaregiverInfo) {
        adherenceDao.insertCaregiver(info)
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .collection("caregiver").document("info")
            .set(info).await()
    }

    override suspend fun deleteCaregiver() {
        adherenceDao.deleteCaregiver()
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .collection("caregiver").document("info")
            .delete().await()
    }

    override suspend fun createCaregiverAlert(
        eventType: String,
        medicineName: String,
        message: String,
        scheduledTime: Long
    ): CaregiverAlert = withContext(Dispatchers.IO) {
        val patientId = auth.currentUser?.uid ?: "local_patient"
        val caregiverInfo = try {
            adherenceDao.getCaregiver().first()
        } catch (e: Exception) {
            null
        }

        val caregiverId = caregiverInfo?.encryptedName?.let { "caregiver_$patientId" } ?: "caregiver_unregistered"
        val fcmToken = caregiverInfo?.caregiverFcmToken

        val alertId = "alert_${System.currentTimeMillis()}_${(1000..9999).random()}"

        Log.d("SMARTMED_CAREGIVER", "Creating caregiver alert: alertId=$alertId, eventType=$eventType")
        Log.d("SMARTMED_CAREGIVER", "FCM token available: ${!fcmToken.isNullOrBlank()}")

        val alert = CaregiverAlert(
            alertId = alertId,
            patientId = patientId,
            caregiverId = caregiverId,
            medicineName = medicineName,
            eventType = eventType,
            message = message,
            scheduledTime = scheduledTime,
            createdAt = System.currentTimeMillis(),
            sentAt = null,
            status = "CREATED",
            acknowledged = false,
            acknowledgedAt = null
        )

        try {
            caregiverAlertDao.insertAlert(alert)
            Log.d("SMARTMED_CAREGIVER", "Alert created in local DB & Firestore (status=CREATED)")
        } catch (e: Exception) {
            Log.e("SMARTMED_CAREGIVER", "Error inserting alert to Room DB: ${e.message}", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("SMARTMED_CAREGIVER", "Notification send attempt starting: alertId=$alertId")

                val sendTime = System.currentTimeMillis()
                val firestoreMap = mapOf(
                    "alertId" to alert.alertId,
                    "patientId" to alert.patientId,
                    "caregiverId" to alert.caregiverId,
                    "medicineName" to alert.medicineName,
                    "eventType" to alert.eventType,
                    "message" to alert.message,
                    "scheduledTime" to alert.scheduledTime,
                    "createdAt" to alert.createdAt,
                    "sentAt" to sendTime,
                    "status" to "SENT",
                    "acknowledged" to false,
                    "acknowledgedAt" to null
                )

                firestore.collection("caregiver_alerts")
                    .document(alertId)
                    .set(firestoreMap).await()

                firestore.collection("users").document(patientId)
                    .collection("caregiver_alerts")
                    .document(alertId)
                    .set(firestoreMap).await()

                val sentAlert = alert.copy(sentAt = sendTime, status = "SENT")
                caregiverAlertDao.insertAlert(sentAlert)

                Log.d("SMARTMED_CAREGIVER", "Notification send attempt succeeded: alertId=$alertId, status=SENT")

                CaregiverNotificationHelper.showNotification(
                    context = context,
                    title = "SmartMed Alert: ${eventType.replace('_', ' ')}",
                    body = "$medicineName: $message",
                    patientId = patientId,
                    alertId = alertId
                )
            } catch (e: Exception) {
                Log.e("SMARTMED_CAREGIVER", "Notification send attempt failed for alert $alertId: ${e.message}", e)
                try {
                    val failedAlert = alert.copy(status = "FAILED")
                    caregiverAlertDao.insertAlert(failedAlert)
                } catch (ignored: Exception) {}
            }
        }

        alert
    }

    override suspend fun acknowledgeCaregiverAlert(alertId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("SMARTMED_CAREGIVER", "Caregiver acknowledging alert: alertId=$alertId")
        try {
            val existing = caregiverAlertDao.getAlertById(alertId)
            if (existing != null && existing.acknowledged) {
                Log.d("SMARTMED_CAREGIVER", "Alert already acknowledged: alertId=$alertId")
                return@withContext true
            }

            val ackTime = System.currentTimeMillis()
            val updates = mapOf(
                "acknowledged" to true,
                "acknowledgedAt" to ackTime,
                "status" to "ACKNOWLEDGED"
            )

            firestore.collection("caregiver_alerts")
                .document(alertId)
                .update(updates).await()

            val patientId = existing?.patientId ?: auth.currentUser?.uid
            if (patientId != null) {
                firestore.collection("users").document(patientId)
                    .collection("caregiver_alerts")
                    .document(alertId)
                    .update(updates).await()
            }

            if (existing != null) {
                val updated = existing.copy(
                    acknowledged = true,
                    acknowledgedAt = ackTime,
                    status = "ACKNOWLEDGED"
                )
                caregiverAlertDao.updateAlert(updated)
            }

            Log.d("SMARTMED_CAREGIVER", "Alert acknowledged successfully: alertId=$alertId")
            true
        } catch (e: Exception) {
            Log.e("SMARTMED_CAREGIVER", "Failed to acknowledge alert $alertId: ${e.message}", e)
            false
        }
    }

    override fun getLocalCaregiverAlerts(): Flow<List<CaregiverAlert>> {
        return caregiverAlertDao.getAllAlerts()
    }

    override fun getRemoteCaregiverAlerts(patientId: String): Flow<List<CaregiverAlert>> {
        return firestore.collection("caregiver_alerts")
            .whereEqualTo("patientId", patientId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<CaregiverAlert>() }
    }

    override fun getRemotePrescriptions(patientId: String): Flow<List<Prescription>> {
        return firestore.collection("users").document(patientId)
            .collection("prescriptions")
            .snapshots()
            .map { it.toObjects<Prescription>() }
    }

    override fun getRemoteAdherence(patientId: String): Flow<List<AdherenceRecord>> {
        return firestore.collection("users").document(patientId)
            .collection("adherence")
            .snapshots()
            .map { it.toObjects<AdherenceRecord>() }
    }

    override fun getRemoteRisk(patientId: String): Flow<RiskAssessment?> {
        return firestore.collection("users").document(patientId)
            .collection("risk")
            .document("latest")
            .snapshots()
            .map { it.toObject<RiskAssessment>() }
    }

    override suspend fun uploadRisk(risk: RiskAssessment) {
        try {
            val userId = auth.currentUser?.uid ?: return
            firestore.collection("users").document(userId)
                .collection("risk").document("latest")
                .set(risk).await()
        } catch (e: Exception) {
            Log.e("MedicineRepository", "Failed to upload risk to Firestore: ${e.message}", e)
        }
    }

    override suspend fun syncLocalToCloud() {
        val userId = auth.currentUser?.uid ?: return
        
        // Sync Prescriptions
        val prescriptions = prescriptionDao.getAllPrescriptions().first()
        for (presc in prescriptions) {
            uploadPrescription(presc)
        }
        
        // Sync History
        val history = historyDao.getAllHistory().first()
        for (item in history) {
            uploadHistory(item)
        }
        
        // Sync Adherence
        val adherence = adherenceDao.getAllAdherenceRecords().first()
        for (record in adherence) {
            uploadAdherence(record)
        }
        
        // Sync Allergies
        val allergies = safetyDao.getAllAllergies().first()
        for (allergy in allergies) {
            firestore.collection("users").document(userId)
                .collection("allergies").document(allergy.allergen)
                .set(allergy).await()
        }
    }

    override suspend fun getMedicinesForCondition(conditionName: String): List<String> = withContext(Dispatchers.IO) {
        medicineConditionDao.getMedicinesForCondition(conditionName)
    }

    override suspend fun getConditionPrescriptions(conditionName: String): List<Prescription> = withContext(Dispatchers.IO) {
        val meds = getMedicinesForCondition(conditionName)
        val allPresc = prescriptionDao.getAllPrescriptions().first()
        allPresc.filter { presc ->
            meds.any { med ->
                com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(presc.medicineName, med)
            }
        }
    }

    override suspend fun getConditionAdherenceRecords(conditionName: String, startTime: Long, endTime: Long): List<AdherenceRecord> = withContext(Dispatchers.IO) {
        val meds = getMedicinesForCondition(conditionName)
        val allRecords = adherenceDao.getAllAdherenceRecords().first()
        allRecords.filter { rec ->
            rec.scheduledTime in startTime..endTime && meds.any { med ->
                com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(rec.medicineName, med)
            }
        }
    }
}
