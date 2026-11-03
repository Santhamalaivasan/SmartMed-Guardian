package com.smartmedguardian.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartmedguardian.MainActivity
import com.smartmedguardian.R
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.repository.MedicineRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MissedDoseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MedicineRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prescriptionId = inputData.getLong("prescriptionId", 0L)
        val medicineName = inputData.getString("medicineName") ?: return Result.failure()
        val dosage = inputData.getString("dosage") ?: ""
        val timeSlot = inputData.getString("time") ?: ""
        val scheduledTime = inputData.getLong("scheduledTime", 0L)
        val inputDoseId = inputData.getString("scheduledDoseId")

        val targetScheduledTime = if (scheduledTime > 0L) scheduledTime else System.currentTimeMillis() - 1800000L
        val doseId = inputDoseId ?: "dose_${prescriptionId}_${timeSlot.lowercase()}_$targetScheduledTime"

        // 1. Atomic Check 1: Query existing AdherenceRecord by stable scheduledDoseId
        val recordByDoseId = repository.getRecordByDoseId(doseId)
        if (recordByDoseId != null) {
            val status = recordByDoseId.status
            if (status == "TAKEN" || status == "ON_TIME" || status == "LATE" || status == "SKIPPED" || status == "SKIP") {
                Log.d("MissedDoseWorker", "Dose $doseId already handled with status=$status. Skipping missed dose check.")
                return Result.success()
            }
            if (status == "MISSED" && recordByDoseId.caregiverNotified) {
                Log.d("MissedDoseWorker", "Duplicate missed dose check blocked for $doseId (Caregiver already notified).")
                return Result.success()
            }
        }

        // 2. Atomic Check 2: Check window for canonical name matches
        val windowStart = targetScheduledTime - (2 * 3600000L)
        val windowEnd = targetScheduledTime + (2 * 3600000L)
        val canonicalTarget = com.smartmedguardian.util.MedicineNameCanonicalizer.canonicalize(medicineName)
        val namesToCheck = listOf(medicineName, canonicalTarget)
        
        val existingRecords = repository.getAdherenceForCanonicalNamesInWindow(namesToCheck, windowStart, windowEnd)
        val isHandledInWindow = existingRecords.any { 
            it.status == "TAKEN" || it.status == "ON_TIME" || it.status == "LATE" || it.status == "SKIPPED" || it.status == "SKIP"
        }

        if (isHandledInWindow) {
            Log.d("MissedDoseWorker", "Dose $medicineName ($timeSlot) taken/skipped in window. Skipping missed dose check.")
            return Result.success()
        }

        // 3. Mark dose as MISSED in MedicineRepository (atomic persistence and caregiver alert dispatch)
        try {
            repository.markDoseMissed(
                scheduledDoseId = doseId,
                medicineName = medicineName,
                scheduledTime = targetScheduledTime,
                dosage = dosage,
                slotName = timeSlot
            )
            showMissedDoseNotification(medicineName, dosage, timeSlot)
        } catch (e: Exception) {
            Log.e("MissedDoseWorker", "Error processing missed dose $doseId: ${e.message}", e)
            return Result.retry()
        }

        return Result.success()
    }

    private fun showMissedDoseNotification(medicineName: String, dosage: String, timeSlot: String) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = com.smartmedguardian.util.NotificationConstants.MEDICATION_REMINDERS_CHANNEL_ID

        val channel = NotificationChannel(
            channelId,
            com.smartmedguardian.util.NotificationConstants.MEDICATION_REMINDERS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        if (!notificationManager.areNotificationsEnabled()) {
            Log.w("MissedDoseWorker", "Notification permission disabled. Notification skipped.")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Missed Dose Alert: $medicineName")
            .setContentText("The scheduled $timeSlot dose of $medicineName ($dosage) was missed.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(("missed_" + medicineName + "_" + timeSlot).hashCode(), notification)
    }
}
