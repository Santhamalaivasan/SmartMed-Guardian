package com.smartmedguardian.worker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.smartmedguardian.MainActivity
import com.smartmedguardian.R
import java.util.Calendar

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_reminders_channel"
        const val ACTION_VERIFY = "com.smartmedguardian.ACTION_VERIFY_MEDICINE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prescriptionId = intent.getLongExtra("prescriptionId", -1L)
        val medicineName = intent.getStringExtra("medicineName") ?: "Medication"
        val dosage = intent.getStringExtra("dosage") ?: ""
        val slotName = intent.getStringExtra("slotName") ?: "Scheduled"
        val timeStr = intent.getStringExtra("timeStr") ?: ""
        val isSnooze = intent.getBooleanExtra("isSnooze", false)

        Log.d("ReminderAlarmReceiver", "Received alarm action=$action for $medicineName ($slotName)")

        if (action == ReminderScheduler.ACTION_SNOOZE_REMINDER) {
            // Cancel current notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = (prescriptionId * 10 + slotName.hashCode()).toInt()
            notificationManager.cancel(notificationId)

            // Schedule snooze for 15 minutes
            val scheduler = ReminderScheduler(context)
            scheduler.scheduleSnooze(prescriptionId, medicineName, slotName, timeStr, 15)

            Toast.makeText(context, "Reminder snoozed for 15 minutes", Toast.LENGTH_SHORT).show()
            return
        }

        if (action == ReminderScheduler.ACTION_MEDICATION_REMINDER) {
            showReminderNotification(
                context = context,
                prescriptionId = prescriptionId,
                medicineName = medicineName,
                dosage = dosage,
                slotName = slotName,
                timeStr = timeStr
            )

            // Re-schedule alarm for tomorrow if this was a standard recurring alarm
            if (!isSnooze && prescriptionId > 0L) {
                val hour = intent.getIntExtra("hour", 8)
                val minute = intent.getIntExtra("minute", 0)
                scheduleNextDayAlarm(context, prescriptionId, medicineName, dosage, slotName, timeStr, hour, minute)
            }
        }
    }

    private fun showReminderNotification(
        context: Context,
        prescriptionId: Long,
        medicineName: String,
        dosage: String,
        slotName: String,
        timeStr: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Medication Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority reminders for taking prescribed medicines on time"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // Target Intent: Opens SmartMed Guardian directly to Verification
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            this.action = ACTION_VERIFY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openVerification", true)
            putExtra("prescriptionId", prescriptionId)
            putExtra("medicineName", medicineName)
            putExtra("slotName", slotName)
            putExtra("scheduledTimeStr", timeStr)
        }

        val requestCode = ReminderScheduler.getRequestCode(prescriptionId, slotName)
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze Action Intent
        val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = ReminderScheduler.ACTION_SNOOZE_REMINDER
            putExtra("prescriptionId", prescriptionId)
            putExtra("medicineName", medicineName)
            putExtra("slotName", slotName)
            putExtra("timeStr", timeStr)
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 100000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (timeStr.isNotBlank()) {
            "Your $slotName dose is due now ($timeStr)."
        } else {
            "Your $slotName dose is due now."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("💊 Medication Reminder: $medicineName")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$medicineName\n$bodyText\nDosage: ${dosage.ifBlank { "As prescribed" }}"))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Verify Now", tapPendingIntent)
            .addAction(0, "Remind Me Later", snoozePendingIntent)
            .build()

        val notificationId = if (prescriptionId > 0) (prescriptionId * 10 + slotName.hashCode()).toInt() else medicineName.hashCode()
        notificationManager.notify(notificationId, notification)
    }

    private fun scheduleNextDayAlarm(
        context: Context,
        prescriptionId: Long,
        medicineName: String,
        dosage: String,
        slotName: String,
        timeStr: String,
        hour: Int,
        minute: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_MEDICATION_REMINDER
            putExtra("prescriptionId", prescriptionId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", dosage)
            putExtra("slotName", slotName)
            putExtra("timeStr", timeStr)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val requestCode = ReminderScheduler.getRequestCode(prescriptionId, slotName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrow.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrow.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("ReminderAlarmReceiver", "Error re-scheduling next day alarm", e)
        }
    }
}
