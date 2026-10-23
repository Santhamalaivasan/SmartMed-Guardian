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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed. Restoring active medication reminders...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prescriptions = repository.getAllPrescriptions().first()
                    for (prescription in prescriptions) {
                        if (prescription.remindersEnabled) {
                            reminderScheduler.scheduleReminders(prescription)
                        }
                    }
                    Log.d("BootReceiver", "Restored reminders for ${prescriptions.size} prescriptions")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error restoring reminders on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
