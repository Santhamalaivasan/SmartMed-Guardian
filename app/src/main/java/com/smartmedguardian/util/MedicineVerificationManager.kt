package com.smartmedguardian.util

import android.util.Log
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.MatchStatus
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.ml.ReferenceMatchResult
import com.smartmedguardian.model.VerificationResult
import com.smartmedguardian.model.VerificationSource
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.worker.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class OcrMatchType {
    CLEAR_MATCH,
    CLEAR_MISMATCH,
    INCONCLUSIVE
}

data class SupportedMedicineInfo(
    val canonicalName: String,
    val searchTokens: List<String>
)

val SUPPORTED_MEDICINES = listOf(
    SupportedMedicineInfo("Adol", listOf("adol")),
    SupportedMedicineInfo("Augmentin", listOf("augmentin", "amoxicillin")),
    SupportedMedicineInfo("Brufen", listOf("brufen", "ibuprofen")),
    SupportedMedicineInfo("Cataflam", listOf("cataflam", "diclofenac")),
    SupportedMedicineInfo("Claritine", listOf("claritine", "loratadine")),
    SupportedMedicineInfo("Dolo-650", listOf("dolo650", "dolo 650", "dolo-650", "dolo")),
    SupportedMedicineInfo("Flagyl", listOf("flagyl", "metronidazole")),
    SupportedMedicineInfo("Glucophage", listOf("glucophage", "metformin")),
    SupportedMedicineInfo("Paramol", listOf("paramol")),
    SupportedMedicineInfo("Rennie", listOf("rennie")),
    SupportedMedicineInfo("Zantac", listOf("zantac", "ranitidine")),
    SupportedMedicineInfo("Zyrtec", listOf("zyrtec", "cetirizine"))
)

@Singleton
class MedicineVerificationManager @Inject constructor() {

    fun verify(
        recognitionResult: RecognitionResult,
        prescriptions: List<Prescription>,
        ocrText: String? = null,
        referenceMatchResult: ReferenceMatchResult? = null,
        targetMedicine: String? = null,
        targetSlot: String? = null,
        targetTime: String? = null,
        currentTime: LocalTime = LocalTime.now(),
        currentDate: LocalDate = LocalDate.now(),
        alreadyVerified: Boolean = false,
        duplicateIngredientName: String? = null,
        prescriptionId: Long? = null
    ): VerificationResult {
        val rawOcr = ocrText?.trim() ?: ""
        val normalizedOcr = normalizeOcrText(rawOcr)

        val cleanTarget = targetMedicine?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        val resolvedSlot = targetSlot?.takeIf { it.isNotBlank() && !it.startsWith("{") } ?: inferSlotFromTime(currentTime)
        val ocrMedicineMatch = matchSupportedMedicineFromOcr(rawOcr)

        fun isMatchingTargetMedicine(detectedName: String, targetName: String): Boolean {
            return MedicineNameCanonicalizer.isSameMedicine(detectedName, targetName)
        }

        fun evaluatePrescriptionStatus(
            prescription: Prescription,
            source: VerificationSource,
            confidenceScore: Float
        ): VerificationResult {
            // Check target medicine mismatch if specified
            if (!cleanTarget.isNullOrBlank() && !isMatchingTargetMedicine(prescription.medicineName, cleanTarget)) {
                return VerificationResult(
                    status = VerificationStatus.MEDICINE_NOT_FOUND,
                    recognitionResult = recognitionResult.copy(name = prescription.medicineName, confidence = confidenceScore),
                    prescription = prescription,
                    message = "⚠️ INCORRECT MEDICINE\nDetected: ${prescription.medicineName}\nExpected Prescription: $cleanTarget\nMedicine does not match your prescription. Dose was not marked as taken.",
                    verificationSource = source
                )
            }

            // Target-slot priority:
            // 1. Explicit targetSlot
            // 2. Explicit targetTime from scheduled prescription
            // 3. Infer period from current clock
            val expectedSlot = targetSlot?.takeIf { it.isNotBlank() } ?: inferSlotFromTime(currentTime, prescription)

            // Diagnostic logging for slot resolution
            val inferredCurrentPeriod = getCurrentTimeSlot(currentTime)
            try {
                Log.d("PERIOD_RESOLUTION", "================ PERIOD RESOLUTION LOG ================")
                Log.d("PERIOD_RESOLUTION", "targetSlot            = ${targetSlot ?: "<none>"}")
                Log.d("PERIOD_RESOLUTION", "targetTime            = ${targetTime ?: "<none>"}")
                Log.d("PERIOD_RESOLUTION", "currentTime           = $currentTime")
                Log.d("PERIOD_RESOLUTION", "inferredCurrentPeriod = $inferredCurrentPeriod")
                Log.d("PERIOD_RESOLUTION", "expectedSlot          = $expectedSlot")
                Log.d("PERIOD_RESOLUTION", "verificationSlot      = $expectedSlot")
                Log.d("PERIOD_RESOLUTION", "=======================================================")
            } catch (ignored: Throwable) {
                // Ignore in local test environment
            }

            // Check duplicate verification
            if (alreadyVerified) {
                return VerificationResult(
                    status = VerificationStatus.ALREADY_VERIFIED,
                    recognitionResult = recognitionResult.copy(name = prescription.medicineName, confidence = confidenceScore),
                    prescription = prescription,
                    message = "⚠️ Dose Already Verified\nThe $expectedSlot dose of ${prescription.medicineName} has already been verified for today.\nDose was not marked as taken again.",
                    verificationSource = source
                )
            }

            if (!duplicateIngredientName.isNullOrBlank()) {
                return VerificationResult(
                    status = VerificationStatus.DUPLICATE_INGREDIENT,
                    recognitionResult = recognitionResult.copy(name = prescription.medicineName, confidence = confidenceScore),
                    prescription = prescription,
                    message = "⚠️ Duplicate Active Ingredient Warning\nAnother medication ($duplicateIngredientName) containing the same active ingredient has already been recorded for this dose window.\nPlease consult your physician before taking this dose.",
                    verificationSource = source
                )
            }

            val isDateActive = isWithinActiveDates(prescription, currentDate)
            val isSlotScheduled = isSlotEnabled(prescription, expectedSlot)
            val isTimeWindowValid = isWithinTimeWindow(prescription, expectedSlot, targetTime, currentTime, currentDate)

            val updatedRecognition = recognitionResult.copy(
                name = prescription.medicineName,
                confidence = confidenceScore
            )

            val scheduledTimeDisplay = getFormattedScheduledTime(prescription, expectedSlot, targetTime)

            return if (isDateActive && isSlotScheduled && isTimeWindowValid) {
                VerificationResult(
                    status = VerificationStatus.CORRECT_MEDICINE,
                    recognitionResult = updatedRecognition,
                    prescription = prescription,
                    message = "✓ Medicine verified successfully\n${prescription.medicineName} — Taken ✓\n$expectedSlot dose — $scheduledTimeDisplay",
                    verificationSource = source
                )
            } else if (!isDateActive) {
                VerificationResult(
                    status = VerificationStatus.UNSCHEDULED_TIME,
                    recognitionResult = updatedRecognition,
                    prescription = prescription,
                    message = "✓ Medicine Identified: ${prescription.medicineName}\n⚠️ Not Scheduled Today (Outside Active Date Range)\nDose was not marked as taken.",
                    verificationSource = source
                )
            } else if (!isSlotScheduled) {
                val scheduledPeriods = getScheduledPeriods(prescription)
                val scheduledStr = if (scheduledPeriods.isNotEmpty()) scheduledPeriods.joinToString(", ") else "None"
                VerificationResult(
                    status = VerificationStatus.UNSCHEDULED_TIME,
                    recognitionResult = updatedRecognition,
                    prescription = prescription,
                    message = "✓ Medicine Identified: ${prescription.medicineName}\n⚠️ $expectedSlot Dose Not Enabled in Prescription\nScheduled Periods: $scheduledStr\nDose was not marked as taken.",
                    verificationSource = source
                )
            } else {
                VerificationResult(
                    status = VerificationStatus.UNSCHEDULED_TIME,
                    recognitionResult = updatedRecognition,
                    prescription = prescription,
                    message = "✓ Medicine Identified: ${prescription.medicineName}\n⚠️ Scanned Outside Verification Window for $expectedSlot Dose ($scheduledTimeDisplay)\nDose was not marked as taken.",
                    verificationSource = source
                )
            }
        }

        // =================================================================
        // Strict Prescription Association when prescriptionId is provided
        // =================================================================
        if (prescriptionId != null && prescriptionId > 0L) {
            val targetPrescription = prescriptions.find { it.id == prescriptionId }
            if (targetPrescription == null) {
                return VerificationResult(
                    status = VerificationStatus.MEDICINE_NOT_FOUND,
                    recognitionResult = recognitionResult,
                    message = "⚠️ ERROR: Selected prescription not found (ID: $prescriptionId). Verification stopped."
                )
            }

            if (alreadyVerified) {
                val expectedSlot = targetSlot?.takeIf { it.isNotBlank() } ?: inferSlotFromTime(currentTime, targetPrescription)
                return VerificationResult(
                    status = VerificationStatus.ALREADY_VERIFIED,
                    recognitionResult = recognitionResult.copy(name = targetPrescription.medicineName),
                    prescription = targetPrescription,
                    message = "⚠️ Dose Already Verified\nThe $expectedSlot dose of ${targetPrescription.medicineName} has already been verified for today.\nDose was not marked as taken again.",
                    verificationSource = ocrMedicineMatch?.let { VerificationSource.OCR_TEXT }
                        ?: referenceMatchResult?.let { VerificationSource.REFERENCE_IMAGE }
                        ?: VerificationSource.CNN_CLASSIFIER
                )
            }

            // Explicit Prescription Priority 1: OCR Text Match
            if (ocrMedicineMatch != null) {
                val detectedName = ocrMedicineMatch.canonicalName
                if (isMatchingTargetMedicine(detectedName, targetPrescription.medicineName)) {
                    return evaluatePrescriptionStatus(
                        prescription = targetPrescription,
                        source = VerificationSource.OCR_TEXT,
                        confidenceScore = 0.95f
                    )
                } else {
                    return VerificationResult(
                        status = VerificationStatus.MEDICINE_NOT_FOUND,
                        recognitionResult = recognitionResult.copy(name = detectedName, confidence = 0.95f),
                        prescription = targetPrescription,
                        message = "⚠️ INCORRECT MEDICINE\nDetected: $detectedName\nExpected Prescription: ${targetPrescription.medicineName}\nMedicine does not match your prescription. Dose was not marked as taken.",
                        verificationSource = VerificationSource.OCR_TEXT
                    )
                }
            }

            // Explicit Prescription Priority 2: Reference Image Match
            if (referenceMatchResult != null) {
                when (referenceMatchResult.status) {
                    MatchStatus.UNCERTAIN -> {
                        return VerificationResult(
                            status = VerificationStatus.UNABLE_TO_VERIFY,
                            recognitionResult = recognitionResult.copy(name = targetPrescription.medicineName, confidence = referenceMatchResult.similarity),
                            prescription = targetPrescription,
                            message = "⚠️ UNABLE TO VERIFY MEDICINE\nUnable to verify this medicine. Please scan again.\nCommon causes: blur, poor lighting, or unsuitable viewing angle.\nDose was not marked as taken.",
                            verificationSource = VerificationSource.REFERENCE_IMAGE
                        )
                    }
                    MatchStatus.MISMATCH, MatchStatus.NO_MATCH -> {
                        return VerificationResult(
                            status = VerificationStatus.MEDICINE_NOT_FOUND,
                            recognitionResult = recognitionResult.copy(name = targetPrescription.medicineName, confidence = referenceMatchResult.similarity),
                            prescription = targetPrescription,
                            message = "⚠️ MEDICINE MISMATCH\nMedicine image does not match your prescription reference for ${targetPrescription.medicineName}.\nPlease check the medicine and try again. Dose was not marked as taken.",
                            verificationSource = VerificationSource.REFERENCE_IMAGE
                        )
                    }
                    MatchStatus.MATCH -> {
                        return evaluatePrescriptionStatus(
                            prescription = targetPrescription,
                            source = VerificationSource.REFERENCE_IMAGE,
                            confidenceScore = referenceMatchResult.similarity
                        )
                    }
                }
            }

            // Explicit Prescription Priority 3: CNN Classifier
            val cnnName = MedicineNameCanonicalizer.canonicalize(recognitionResult.name)
            if (recognitionResult.isKnownEnough && recognitionResult.confidence >= 0.70f && recognitionResult.top2Margin >= 0.20f) {
                if (isMatchingTargetMedicine(cnnName, targetPrescription.medicineName)) {
                    return evaluatePrescriptionStatus(
                        prescription = targetPrescription,
                        source = VerificationSource.CNN_CLASSIFIER,
                        confidenceScore = recognitionResult.confidence
                    )
                }
            }

            return VerificationResult(
                status = VerificationStatus.UNKNOWN_MEDICINE,
                recognitionResult = recognitionResult,
                prescription = targetPrescription,
                message = "UNKNOWN MEDICINE: Unable to verify medicine against ${targetPrescription.medicineName}.\nDose was not marked as taken.",
                verificationSource = VerificationSource.UNCERTAIN
            )
        }

        // =================================================================
        // Priority Step 1: OCR / Text Extraction (PRIMARY METHOD)
        // =================================================================
        if (ocrMedicineMatch != null) {
            val detectedMedicineName = ocrMedicineMatch.canonicalName

            // Check against targetMedicine if provided
            if (!cleanTarget.isNullOrBlank() && !isMatchingTargetMedicine(detectedMedicineName, cleanTarget)) {
                val incorrectResult = VerificationResult(
                    status = VerificationStatus.MEDICINE_NOT_FOUND,
                    recognitionResult = recognitionResult.copy(name = detectedMedicineName, confidence = 0.95f),
                    message = "⚠️ INCORRECT MEDICINE\nDetected: $detectedMedicineName\nExpected Prescription: $cleanTarget\nMedicine does not match your prescription. Dose was not marked as taken.",
                    verificationSource = VerificationSource.OCR_TEXT
                )

                logDiagnostics(
                    rawOcrText = rawOcr,
                    normalizedOcrText = normalizedOcr,
                    ocrMedicineMatch = detectedMedicineName,
                    prescriptions = prescriptions,
                    currentPeriod = resolvedSlot,
                    finalResult = "INCORRECT_TARGET_MISMATCH"
                )

                return incorrectResult
            }

            // Find matching prescription
            val matchedPrescription = prescriptions.find { prescription ->
                val pNorm = normalizeMedicineName(prescription.medicineName)
                val ocrNorm = normalizeMedicineName(detectedMedicineName)
                pNorm.isNotBlank() && (pNorm == ocrNorm || pNorm.contains(ocrNorm) || ocrNorm.contains(pNorm))
            }

            if (matchedPrescription != null) {
                val decision = evaluatePrescriptionStatus(
                    prescription = matchedPrescription,
                    source = VerificationSource.OCR_TEXT,
                    confidenceScore = 0.95f
                )

                logDiagnostics(
                    rawOcrText = rawOcr,
                    normalizedOcrText = normalizedOcr,
                    ocrMedicineMatch = detectedMedicineName,
                    prescriptions = prescriptions,
                    currentPeriod = resolvedSlot,
                    finalResult = decision.status.name
                )

                return decision
            } else {
                val prescribedNames = prescriptions.joinToString(", ") { it.medicineName }.ifEmpty { "None" }
                val incorrectMsg = "⚠️ INCORRECT MEDICINE\nDetected: $detectedMedicineName\nPrescribed: $prescribedNames\nMedicine does not match your prescription. Dose was not marked as taken."

                val incorrectResult = VerificationResult(
                    status = VerificationStatus.MEDICINE_NOT_FOUND,
                    recognitionResult = recognitionResult.copy(name = detectedMedicineName, confidence = 0.95f),
                    message = incorrectMsg,
                    verificationSource = VerificationSource.OCR_TEXT
                )

                logDiagnostics(
                    rawOcrText = rawOcr,
                    normalizedOcrText = normalizedOcr,
                    ocrMedicineMatch = detectedMedicineName,
                    prescriptions = prescriptions,
                    currentPeriod = resolvedSlot,
                    finalResult = "INCORRECT"
                )

                return incorrectResult
            }
        }

        // =================================================================
        // Priority Step 2: User Reference Photo Visual Matcher (FALLBACK)
        // =================================================================
        if (referenceMatchResult != null) {
            when (referenceMatchResult.status) {
                MatchStatus.MATCH -> {
                    val targetPrescription = referenceMatchResult.prescription
                    if (targetPrescription != null) {
                        val decision = evaluatePrescriptionStatus(
                            prescription = targetPrescription,
                            source = VerificationSource.REFERENCE_IMAGE,
                            confidenceScore = referenceMatchResult.similarity
                        )
                        logDiagnostics(rawOcrText = rawOcr, normalizedOcrText = normalizedOcr, ocrMedicineMatch = "NONE", prescriptions = prescriptions, currentPeriod = resolvedSlot, finalResult = decision.status.name)
                        return decision
                    }
                }
                MatchStatus.UNCERTAIN -> {
                    val targetPrescription = referenceMatchResult.prescription
                    val recResult = recognitionResult.copy(
                        name = targetPrescription?.medicineName ?: "Uncertain Medicine",
                        confidence = referenceMatchResult.similarity
                    )
                    val decision = VerificationResult(
                        status = VerificationStatus.UNABLE_TO_VERIFY,
                        recognitionResult = recResult,
                        prescription = targetPrescription,
                        message = "⚠️ UNABLE TO VERIFY MEDICINE\nUnable to verify this medicine. Please scan again.\nCommon causes: blur, poor lighting, or unsuitable viewing angle.\nDose was not marked as taken.",
                        verificationSource = VerificationSource.REFERENCE_IMAGE
                    )
                    logDiagnostics(rawOcrText = rawOcr, normalizedOcrText = normalizedOcr, ocrMedicineMatch = "NONE", prescriptions = prescriptions, currentPeriod = resolvedSlot, finalResult = "UNCERTAIN")
                    return decision
                }
                MatchStatus.MISMATCH, MatchStatus.NO_MATCH -> {
                    // In general scan mode, if reference photo comparison is MISMATCH/NO_MATCH, fall through to Priority Step 3 (CNN Classification)!
                }
            }
        }

        // =================================================================
        // Priority Step 3: 12-Class CNN Classifier (FALLBACK)
        // =================================================================
        val cnnPredictedName = MedicineNameCanonicalizer.canonicalize(recognitionResult.name)
        val cnnConfidence = recognitionResult.confidence
        val normalizedCnnName = normalizeTextForMatching(cnnPredictedName)

        val matchedCnnPrescription = prescriptions.find { prescription ->
            val pNorm = normalizeTextForMatching(prescription.medicineName)
            pNorm.isNotBlank() && (pNorm == normalizedCnnName || pNorm.contains(normalizedCnnName) || normalizedCnnName.contains(pNorm))
        }

        if (recognitionResult.isKnownEnough && cnnConfidence >= 0.70f && recognitionResult.top2Margin >= 0.20f) {
            if (!cnnPredictedName.lowercase(Locale.ROOT).contains("other") && matchedCnnPrescription != null) {
                val decision = evaluatePrescriptionStatus(
                    prescription = matchedCnnPrescription,
                    source = VerificationSource.CNN_CLASSIFIER,
                    confidenceScore = cnnConfidence
                )

                logDiagnostics(
                    rawOcrText = rawOcr,
                    normalizedOcrText = normalizedOcr,
                    ocrMedicineMatch = "NONE",
                    prescriptions = prescriptions,
                    currentPeriod = resolvedSlot,
                    finalResult = decision.status.name
                )

                return decision
            }
        }

        // =================================================================
        // Default Step 4: Unknown Medicine / Verification Failed
        // =================================================================
        val unknownResult = VerificationResult(
            status = VerificationStatus.UNKNOWN_MEDICINE,
            recognitionResult = recognitionResult,
            message = "UNKNOWN MEDICINE: Unable to identify medicine from package text or visual scan.\nDose was not marked as taken.",
            verificationSource = VerificationSource.UNCERTAIN
        )

        logDiagnostics(
            rawOcrText = rawOcr,
            normalizedOcrText = normalizedOcr,
            ocrMedicineMatch = "NONE",
            prescriptions = prescriptions,
            currentPeriod = resolvedSlot,
            finalResult = "UNKNOWN"
        )

        return unknownResult
    }

    fun matchSupportedMedicineFromOcr(rawOcr: String): SupportedMedicineInfo? {
        if (rawOcr.isBlank()) return null

        val normalized = normalizeOcrText(rawOcr)
        val compact = normalized.replace("[^a-z0-9]".toRegex(), "")
        val words = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

        for (med in SUPPORTED_MEDICINES) {
            for (token in med.searchTokens) {
                val compactToken = token.replace("[^a-z0-9]".toRegex(), "")
                
                // Substring match on compact string (e.g. "dolo650" inside "...dolo650tablets...")
                if (compactToken.length >= 3 && compact.contains(compactToken)) {
                    return med
                }

                // Distinct word match in words list
                if (words.contains(token.lowercase(Locale.ROOT))) {
                    return med
                }
            }
        }
        return null
    }

    fun normalizeOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""
        return rawText
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9\\s-]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun normalizeMedicineName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace("\\b(mg|mcg|g|ml|tablets?|tabs?|capsules?|syrup|ip|bp|usp|search|found)\\b".toRegex(), "")
            .replace("[^a-z0-9]".toRegex(), "")
    }

    fun parseTimeStringToHourMinute(timeStr: String, defaultHour: Int): Pair<Int, Int> {
        if (timeStr.isBlank()) return Pair(defaultHour, 0)
        val clean = timeStr.trim().uppercase(Locale.ROOT)
        return try {
            val isPm = clean.contains("PM")
            val isAm = clean.contains("AM")
            val digits = clean.replace("[^0-9:]".toRegex(), "")
            val parts = digits.split(":")
            var hour = parts[0].toInt()
            val minute = if (parts.size > 1) parts[1].toInt() else 0

            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0

            Pair(hour, minute)
        } catch (e: Exception) {
            Pair(defaultHour, 0)
        }
    }

    fun inferSlotFromTime(currentTime: LocalTime, prescription: Prescription? = null): String {
        if (prescription != null) {
            val enabledSlots = mutableListOf<Pair<String, Pair<Int, Int>>>()
            if (prescription.morning) enabledSlots.add("Morning" to parseTimeStringToHourMinute(prescription.morningTime, 8))
            if (prescription.afternoon) enabledSlots.add("Afternoon" to parseTimeStringToHourMinute(prescription.afternoonTime, 13))
            if (prescription.evening) enabledSlots.add("Evening" to parseTimeStringToHourMinute(prescription.eveningTime, 18))
            if (prescription.night) enabledSlots.add("Night" to parseTimeStringToHourMinute(prescription.nightTime, 20))

            if (enabledSlots.isNotEmpty()) {
                val currentMinutes = currentTime.hour * 60 + currentTime.minute
                var bestSlot = enabledSlots.first().first
                var minDiff = Int.MAX_VALUE

                for ((slot, timePair) in enabledSlots) {
                    val scheduledMins = timePair.first * 60 + timePair.second
                    var diff = Math.abs(currentMinutes - scheduledMins)
                    if (diff > 720) diff = 1440 - diff
                    if (diff < minDiff) {
                        minDiff = diff
                        bestSlot = slot
                    }
                }

                if (minDiff <= 240) { // Within 4 hours of scheduled time
                    return bestSlot
                }
            }
        }

        return getCurrentTimeSlot(currentTime)
    }

    fun getCurrentTimeSlot(time: LocalTime = LocalTime.now()): String {
        val hour = time.hour
        return when {
            hour in 5..11 -> "Morning"
            hour in 12..16 -> "Afternoon"
            hour in 17..19 -> "Evening"
            else -> "Night" // 20:00 - 04:59
        }
    }

    fun getCurrentTimeSlot(cal: Calendar): String {
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val millis = cal.get(Calendar.MILLISECOND)
        val time = LocalTime.of(hour, minute, second, millis * 1_000_000)
        return getCurrentTimeSlot(time)
    }

    fun isWithinTimeWindow(
        prescription: Prescription,
        slotName: String,
        explicitTimeStr: String? = null,
        currentTime: LocalTime = LocalTime.now(),
        currentDate: LocalDate = LocalDate.now(),
        maxAllowedDiffMinutes: Int = 240
    ): Boolean {
        val scheduledTimeStr = explicitTimeStr?.takeIf { it.isNotBlank() } ?: when (slotName.trim().lowercase(Locale.ROOT)) {
            "morning" -> prescription.morningTime
            "afternoon" -> prescription.afternoonTime
            "evening" -> prescription.eveningTime
            "night" -> prescription.nightTime
            else -> "08:00"
        }

        val (scheduledHour, scheduledMinute) = parseTimeStringToHourMinute(scheduledTimeStr, 8)
        val scheduledMins = scheduledHour * 60 + scheduledMinute
        val currentMins = currentTime.hour * 60 + currentTime.minute

        var diffMins = Math.abs(currentMins - scheduledMins)
        if (diffMins > 720) {
            diffMins = 1440 - diffMins
        }

        return diffMins <= maxAllowedDiffMinutes
    }

    fun getFormattedScheduledTime(
        prescription: Prescription,
        slotName: String,
        explicitTimeStr: String? = null
    ): String {
        val timeStr = explicitTimeStr?.takeIf { it.isNotBlank() } ?: when (slotName.trim().lowercase(Locale.ROOT)) {
            "morning" -> prescription.morningTime
            "afternoon" -> prescription.afternoonTime
            "evening" -> prescription.eveningTime
            "night" -> prescription.nightTime
            else -> "08:00"
        }
        val (hour, minute) = parseTimeStringToHourMinute(timeStr, 8)
        return ReminderScheduler.format12HourTime(hour, minute)
    }

    fun isSlotEnabled(prescription: Prescription, timeSlot: String): Boolean {
        return when (timeSlot.trim().lowercase(Locale.ROOT)) {
            "morning" -> prescription.morning
            "afternoon" -> prescription.afternoon
            "evening" -> prescription.evening
            "night" -> prescription.night
            else -> false
        }
    }

    fun isWithinActiveDates(prescription: Prescription, nowLocalDate: LocalDate = LocalDate.now()): Boolean {
        val startLocalDate = if (prescription.startDate > 0L) {
            Instant.ofEpochMilli(prescription.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        } else null

        val endLocalDate = if (prescription.endDate > 0L) {
            Instant.ofEpochMilli(prescription.endDate).atZone(ZoneId.systemDefault()).toLocalDate()
        } else null

        val isAfterStart = startLocalDate == null || !nowLocalDate.isBefore(startLocalDate)
        val isBeforeEnd = endLocalDate == null || !nowLocalDate.isAfter(endLocalDate)
        return isAfterStart && isBeforeEnd
    }

    fun parseHourMinute(timeStr: String, defaultHour: Int): Pair<Int, Int> {
        return parseTimeStringToHourMinute(timeStr, defaultHour)
    }

    fun getScheduledPeriods(prescription: Prescription): List<String> {
        val periods = mutableListOf<String>()
        if (prescription.morning) periods.add("Morning")
        if (prescription.afternoon) periods.add("Afternoon")
        if (prescription.evening) periods.add("Evening")
        if (prescription.night) periods.add("Night")
        return periods
    }

    fun getSlotTimeWindowMillis(
        slotName: String,
        currentTime: LocalTime = LocalTime.now(),
        currentDate: LocalDate = LocalDate.now()
    ): Pair<Long, Long> {
        val normSlot = slotName.trim().lowercase(Locale.ROOT)
        val effectiveDate = if (normSlot == "night" && currentTime.isBefore(LocalTime.of(5, 0))) {
            currentDate.minusDays(1)
        } else {
            currentDate
        }

        val (startLocalTime, endLocalTime, isNextDay) = when (normSlot) {
            "morning" -> Triple(LocalTime.of(5, 0), LocalTime.of(11, 59, 59), false)
            "afternoon" -> Triple(LocalTime.of(12, 0), LocalTime.of(16, 59, 59), false)
            "evening" -> Triple(LocalTime.of(17, 0), LocalTime.of(19, 59, 59), false)
            "night" -> Triple(LocalTime.of(20, 0), LocalTime.of(4, 59, 59), true)
            else -> Triple(LocalTime.of(0, 0), LocalTime.of(23, 59, 59), false)
        }

        val zoneId = ZoneId.systemDefault()
        val startMillis = effectiveDate.atTime(startLocalTime).atZone(zoneId).toInstant().toEpochMilli()
        val endDate = if (isNextDay) effectiveDate.plusDays(1) else effectiveDate
        val endMillis = endDate.atTime(endLocalTime).atZone(zoneId).toInstant().toEpochMilli()

        return Pair(startMillis, endMillis)
    }

    fun getScheduledTimeMillis(
        prescription: Prescription,
        slotName: String,
        explicitTimeStr: String? = null,
        currentTime: LocalTime = LocalTime.now(),
        currentDate: LocalDate = LocalDate.now()
    ): Long {
        val normSlot = slotName.trim().lowercase(Locale.ROOT)
        val effectiveDate = if (normSlot == "night" && currentTime.isBefore(LocalTime.of(5, 0))) {
            currentDate.minusDays(1)
        } else {
            currentDate
        }

        val timeStr = explicitTimeStr?.takeIf { it.isNotBlank() } ?: when (normSlot) {
            "morning" -> prescription.morningTime
            "afternoon" -> prescription.afternoonTime
            "evening" -> prescription.eveningTime
            "night" -> prescription.nightTime
            else -> "08:00"
        }

        val (hour, minute) = parseTimeStringToHourMinute(timeStr, 8)
        val zoneId = ZoneId.systemDefault()
        return effectiveDate.atTime(hour, minute).atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun logDiagnostics(
        rawOcrText: String,
        normalizedOcrText: String,
        ocrMedicineMatch: String,
        prescriptions: List<Prescription>,
        currentPeriod: String,
        finalResult: String
    ) {
        try {
            val prescribedNames = prescriptions.joinToString(", ") { it.medicineName }.ifEmpty { "NONE" }
            Log.d("SMARTMED_VERIFICATION", "================ SMARTMED VERIFICATION DIAGNOSTICS ================")
            Log.d("SMARTMED_VERIFICATION", "RAW OCR TEXT         : ${if (rawOcrText.isBlank()) "<empty>" else "[REDACTED_OCR]"}")
            Log.d("SMARTMED_VERIFICATION", "NORMALIZED OCR TEXT  : ${if (normalizedOcrText.isBlank()) "<empty>" else "[REDACTED_OCR]"}")
            Log.d("SMARTMED_VERIFICATION", "OCR MEDICINE MATCH   : ${if (ocrMedicineMatch.isNotBlank()) "[REDACTED_MEDICINE]" else "NONE"}")
            Log.d("SMARTMED_VERIFICATION", "PRESCRIPTION MEDICINE: ${if (prescribedNames == "NONE") "NONE" else "[REDACTED_PRESCRIPTION]"}")
            Log.d("SMARTMED_VERIFICATION", "CURRENT TIME PERIOD  : $currentPeriod")
            Log.d("SMARTMED_VERIFICATION", "FINAL RESULT         : $finalResult")
            Log.d("SMARTMED_VERIFICATION", "===================================================================")
        } catch (ignored: Throwable) {
            // Ignore in local unit test environment where android.util.Log is not mocked
        }
    }

    fun normalizeTextForMatching(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), "")
    }
}
