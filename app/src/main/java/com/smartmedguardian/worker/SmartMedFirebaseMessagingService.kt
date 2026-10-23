package com.smartmedguardian.worker

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.util.CaregiverNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class SmartMedFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var repository: MedicineRepository

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var firestore: FirebaseFirestore

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SMARTMED_CAREGIVER", "FCM token generated/updated")
        Log.d("SMARTMED_CAREGIVER", "FCM token availability: ${token.isNotBlank()}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val tokenData = mapOf(
                        "caregiverFcmToken" to token,
                        "fcmToken" to token,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    firestore.collection("users").document(userId)
                        .collection("caregiver").document("info")
                        .set(tokenData, SetOptions.merge()).await()

                    firestore.collection("users").document(userId)
                        .set(tokenData, SetOptions.merge()).await()
                }

                val currentCaregiver = repository.getCaregiver().first()
                if (currentCaregiver != null) {
                    val updated = currentCaregiver.copy(caregiverFcmToken = token)
                    repository.saveCaregiver(updated)
                }
                Log.d("SMARTMED_CAREGIVER", "Caregiver token saved to Firebase & Room DB")
            } catch (e: Exception) {
                Log.e("SMARTMED_CAREGIVER", "Failed to update caregiver FCM token: ${e.message}", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val alertId = remoteMessage.data["alertId"] ?: remoteMessage.data["id"]
        val patientId = remoteMessage.data["patientId"] ?: "local"
        val eventType = remoteMessage.data["eventType"] ?: "ALERT"
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "SmartMed Caregiver Alert"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "A medication alert was generated."

        Log.d("SMARTMED_CAREGIVER", "Notification received: alertId=$alertId, patientId=$patientId, eventType=$eventType")

        CaregiverNotificationHelper.showNotification(
            context = applicationContext,
            title = title,
            body = body,
            patientId = patientId,
            alertId = alertId
        )
        Log.d("SMARTMED_CAREGIVER", "Notification displayed for alertId=$alertId")
    }
}
