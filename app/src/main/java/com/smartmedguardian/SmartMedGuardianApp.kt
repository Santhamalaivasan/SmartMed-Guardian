package com.smartmedguardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmedguardian.worker.SyncWorker
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmartMedGuardianApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Manual initialization with placeholder if auto-init failed
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:123456789:android:123456789")
                    .setProjectId("smart-med-guardian-placeholder")
                    .setApiKey("placeholder-api-key")
                    .build()
                FirebaseApp.initializeApp(this, options)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        scheduleSync()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val medChannel = NotificationChannel(
            com.smartmedguardian.util.NotificationConstants.MEDICATION_REMINDERS_CHANNEL_ID,
            com.smartmedguardian.util.NotificationConstants.MEDICATION_REMINDERS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority reminders for taking prescribed medicines on time"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(medChannel)

        val caregiverChannel = NotificationChannel(
            com.smartmedguardian.util.NotificationConstants.CAREGIVER_ALERTS_CHANNEL_ID,
            com.smartmedguardian.util.NotificationConstants.CAREGIVER_ALERTS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications sent to caregivers when prescribed medication is taken or missed"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(caregiverChannel)
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CloudSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
