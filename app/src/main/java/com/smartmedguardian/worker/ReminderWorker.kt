package com.smartmedguardian.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartmedguardian.R

import android.app.PendingIntent
import android.content.Intent
import com.smartmedguardian.MainActivity

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val medicineName = inputData.getString("medicineName") ?: return Result.failure()
        val dosage = inputData.getString("dosage") ?: ""
        val time = inputData.getString("time") ?: ""

        showNotification(medicineName, "$dosage - $time")

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
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

        // Actions
        val takenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "TAKEN"
            putExtra("medicineName", title)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(context, 1, takenIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "SKIP"
            putExtra("medicineName", title)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(context, 2, skipIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Time for: $title")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Mark Taken", takenPendingIntent)
            .addAction(0, "Skip", skipPendingIntent)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }
}
