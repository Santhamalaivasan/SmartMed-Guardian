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
        val medicineName = inputData.getString("medicineName") ?: return Result.failure()
        val dosage = inputData.getString("dosage") ?: ""
        val timeSlot = inputData.getString("time") ?: ""
        val scheduledTime = inputData.getLong("scheduledTime", 0L)

        val targetScheduledTime = if (scheduledTime > 0L) scheduledTime else System.currentTimeMillis() - 3600000L

        // Window: +- 2 hours around targetScheduledTime
        val windowStart = targetScheduledTime - (2 * 3600000L)
        val windowEnd = targetScheduledTime + (2 * 3600000L)

        // Check existing adherence records for this medicine in this time window
        val existingRecords = repository.getAdherenceForMedicineInWindow(medicineName, windowStart, windowEnd)

        // If any record exists for this dose (TAKEN, SKIPPED, SKIP, LATE, or already MISSED), do nothing
        val isHandled = existingRecords.any { 
            it.status == "TAKEN" || it.status == "SKIPPED" || it.status == "SKIP" || it.status == "MISSED" || it.status == "LATE"
        }

        if (!isHandled) {
            val missedRecord = AdherenceRecord(
                medicineName = medicineName,
                scheduledTime = targetScheduledTime,
                actualTime = null,
                status = "MISSED"
            )
            repository.insertAdherenceRecord(missedRecord)
            
            // Create Caregiver Alert for missed dose
            try {
                repository.createCaregiverAlert(
                    eventType = "MISSED_DOSE",
                    medicineName = medicineName,
                    message = "Missed $timeSlot dose (${dosage.ifBlank { "as prescribed" }})",
                    scheduledTime = targetScheduledTime
                )
            } catch (e: Exception) {
                Log.e("SMARTMED_CAREGIVER", "Failed to create caregiver alert for missed dose: ${e.message}", e)
            }

            showMissedDoseNotification(medicineName, dosage, timeSlot)
        }

        return Result.success()
    }

    private fun showMissedDoseNotification(medicineName: String, dosage: String, timeSlot: String) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medicine_reminder_channel"

        val channel = NotificationChannel(
            channelId,
            "Medicine Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Missed Dose Alert: $medicineName")
            .setContentText("You missed your $timeSlot dose ($dosage).")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(("missed_" + medicineName + "_" + timeSlot).hashCode(), notification)
    }
}
