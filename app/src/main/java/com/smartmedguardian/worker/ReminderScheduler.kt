package com.smartmedguardian.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smartmedguardian.data.entity.Prescription
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val ACTION_MEDICATION_REMINDER = "com.smartmedguardian.ACTION_MEDICATION_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.smartmedguardian.ACTION_SNOOZE_REMINDER"

        fun getRequestCode(prescriptionId: Long, slotName: String): Int {
            val slotIndex = when (slotName.lowercase(Locale.ROOT)) {
                "morning" -> 1
                "afternoon" -> 2
                "evening" -> 3
                "night" -> 4
                else -> 5
            }
            return ((prescriptionId % 100000).toInt() * 10) + slotIndex
        }

        fun parseHourMinute(timeStr: String, defaultHour: Int): Pair<Int, Int> {
            return try {
                val parts = timeStr.trim().split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                Pair(hour, minute)
            } catch (e: Exception) {
                Pair(defaultHour, 0)
            }
        }

        fun format12HourTime(hour: Int, minute: Int): String {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            return SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
        }
    }

    fun scheduleReminders(prescription: Prescription) {
        if (!prescription.remindersEnabled) {
            cancelAllReminders(prescription.id)
            return
        }

        val slots = listOf(
            Triple("Morning", prescription.morning, prescription.morningTime to 8),
            Triple("Afternoon", prescription.afternoon, prescription.afternoonTime to 13),
            Triple("Evening", prescription.evening, prescription.eveningTime to 20),
            Triple("Night", prescription.night, prescription.nightTime to 22)
        )

        for ((slotName, enabled, timePair) in slots) {
            if (enabled) {
                val (timeStr, defaultHour) = timePair
                val (hour, minute) = parseHourMinute(timeStr, defaultHour)
                scheduleExactAlarm(prescription, slotName, hour, minute)
                scheduleMissedDoseWork(prescription, slotName, hour, minute)
            } else {
                cancelAlarmSlot(prescription.id, slotName)
                cancelWorkSlot(prescription.id, slotName)
            }
        }
    }

    fun scheduleExactAlarm(
        prescription: Prescription,
        slotName: String,
        hour: Int,
        minute: Int
    ) {
        val now = Calendar.getInstance()

        if (prescription.endDate > 0 && now.timeInMillis > prescription.endDate) {
            cancelAlarmSlot(prescription.id, slotName)
            return
        }

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (prescription.startDate > 0 && target.timeInMillis < prescription.startDate) {
            val startCal = Calendar.getInstance().apply { timeInMillis = prescription.startDate }
            target.set(Calendar.YEAR, startCal.get(Calendar.YEAR))
            target.set(Calendar.MONTH, startCal.get(Calendar.MONTH))
            target.set(Calendar.DAY_OF_MONTH, startCal.get(Calendar.DAY_OF_MONTH))
        }

        while (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (prescription.endDate > 0 && target.timeInMillis > prescription.endDate) {
            cancelAlarmSlot(prescription.id, slotName)
            return
        }

        val requestCode = getRequestCode(prescription.id, slotName)
        val formattedTime = format12HourTime(hour, minute)

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            putExtra("prescriptionId", prescription.id)
            putExtra("medicineName", prescription.medicineName)
            putExtra("dosage", prescription.dosage)
            putExtra("slotName", slotName)
            putExtra("timeStr", formattedTime)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("ReminderScheduler", "Exact alarm scheduled for ${prescription.medicineName} ($slotName) at ${target.time}")
        } catch (e: SecurityException) {
            Log.e("ReminderScheduler", "SecurityException setting exact alarm", e)
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pendingIntent
            )
        }
    }

    fun scheduleSnooze(
        prescriptionId: Long,
        medicineName: String,
        slotName: String,
        timeStr: String,
        snoozeMinutes: Int = 15
    ) {
        val snoozeTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val requestCode = getRequestCode(prescriptionId, slotName) + 500000

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            putExtra("prescriptionId", prescriptionId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", "")
            putExtra("slotName", slotName)
            putExtra("timeStr", timeStr)
            putExtra("isSnooze", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
            }
            Log.d("ReminderScheduler", "Snooze alarm scheduled for $medicineName in $snoozeMinutes minutes")
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "Error scheduling snooze alarm", e)
        }
    }

    private fun scheduleMissedDoseWork(
        prescription: Prescription,
        slotName: String,
        hour: Int,
        minute: Int
    ) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val missedTargetTime = target.timeInMillis + (60 * 60 * 1000L) // +1 hour grace
        val missedInitialDelay = (missedTargetTime - now.timeInMillis).coerceAtLeast(0L)

        val missedInputData = workDataOf(
            "prescriptionId" to prescription.id,
            "medicineName" to prescription.medicineName,
            "dosage" to prescription.dosage,
            "time" to slotName,
            "scheduledTime" to target.timeInMillis
        )

        val missedWorkName = "missed_${prescription.id}_${slotName.lowercase(Locale.ROOT)}"
        val missedWorkRequest = PeriodicWorkRequestBuilder<MissedDoseWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(missedInitialDelay, TimeUnit.MILLISECONDS)
            .setInputData(missedInputData)
            .build()

        workManager.enqueueUniquePeriodicWork(
            missedWorkName,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            missedWorkRequest
        )
    }

    fun cancelAlarmSlot(prescriptionId: Long, slotName: String) {
        val requestCode = getRequestCode(prescriptionId, slotName)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        // Cancel snooze pending intent if active
        val snoozeRequestCode = requestCode + 500000
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (snoozePendingIntent != null) {
            alarmManager.cancel(snoozePendingIntent)
            snoozePendingIntent.cancel()
        }
    }

    private fun cancelWorkSlot(prescriptionId: Long, slotName: String) {
        val missedWorkName = "missed_${prescriptionId}_${slotName.lowercase(Locale.ROOT)}"
        workManager.cancelUniqueWork(missedWorkName)
    }

    fun cancelAllReminders(prescriptionId: Long) {
        val slots = listOf("Morning", "Afternoon", "Evening", "Night")
        for (slot in slots) {
            cancelAlarmSlot(prescriptionId, slot)
            cancelWorkSlot(prescriptionId, slot)
        }
    }
}
