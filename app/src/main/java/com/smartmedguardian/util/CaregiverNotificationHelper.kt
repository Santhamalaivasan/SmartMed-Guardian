package com.smartmedguardian.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartmedguardian.MainActivity
import com.smartmedguardian.R
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.worker.ReminderScheduler
import java.util.Calendar

object CaregiverNotificationHelper {

    const val CHANNEL_ID = "caregiver_notifications_channel"
    const val CHANNEL_NAME = "Caregiver Medication Alerts"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications sent to caregivers when prescribed medication is taken or missed"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun format12HourTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return ReminderScheduler.format12HourTime(hour, minute)
    }

    fun buildNotificationPayload(
        record: AdherenceRecord,
        explicitTimeStr: String? = null
    ): Pair<String, String> {
        val actualTimeStr = format12HourTime(record.actualTime ?: System.currentTimeMillis())
        val scheduledTimeStr = if (!explicitTimeStr.isNullOrBlank()) {
            explicitTimeStr
        } else {
            format12HourTime(record.scheduledTime)
        }

        return if (record.status == "LATE") {
            val title = "Medication Taken Late"
            val body = "${record.medicineName} was verified at $actualTimeStr. Scheduled time was $scheduledTimeStr."
            Pair(title, body)
        } else {
            val title = "Medication Taken"
            val body = "${record.medicineName} was verified as taken at $actualTimeStr."
            Pair(title, body)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        patientId: String = "local",
        alertId: String? = null
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openCaregiverDashboard", true)
            putExtra("patientId", patientId)
            if (!alertId.isNullOrBlank()) {
                putExtra("alertId", alertId)
            }
        }

        val requestCode = (System.currentTimeMillis() % 10000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notification)
        Log.d("CaregiverNotification", "Posted local caregiver notification: $title - $body")
    }
}
