package com.smartmedguardian.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartmedguardian.repository.MedicineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: MedicineRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            Log.d("BootReceiver", "System event ($action) received. Recalculating and restoring medication reminders...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prescriptions = repository.getAllPrescriptions().first()
                    for (prescription in prescriptions) {
                        if (prescription.remindersEnabled) {
                            reminderScheduler.scheduleReminders(prescription)
                        }
                    }
                    Log.d("BootReceiver", "Successfully rescheduled reminders for ${prescriptions.size} active prescriptions on $action")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling reminders on $action", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
