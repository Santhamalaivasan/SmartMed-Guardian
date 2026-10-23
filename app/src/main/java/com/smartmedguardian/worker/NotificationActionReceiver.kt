package com.smartmedguardian.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.repository.MedicineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: MedicineRepository

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicineName") ?: return
        val status = intent.action ?: return
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicineName.hashCode())

        // Only record SKIPPED/SKIP. "TAKEN" requires successful camera verification.
        if (status != "TAKEN") {
            CoroutineScope(Dispatchers.IO).launch {
                repository.insertAdherenceRecord(
                    AdherenceRecord(
                        medicineName = medicineName,
                        scheduledTime = System.currentTimeMillis(),
                        actualTime = null,
                        status = status
                    )
                )
            }
        }
    }
}
