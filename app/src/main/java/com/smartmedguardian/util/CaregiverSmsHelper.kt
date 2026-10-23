package com.smartmedguardian.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaregiverSmsHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // Thread-safe set of processed adherence event IDs to prevent duplicate SMS sends
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * Sends an SMS notification to the caregiver if permission is granted and the event has not been sent.
     *
     * @param eventId Stable unique identifier for the adherence record/event
     * @param phoneNumber Decrypted recipient phone number
     * @param message Text payload to be sent
     * @return True if the SMS was handed off to SmsManager successfully, false otherwise.
     */
    fun sendSms(eventId: String, phoneNumber: String, message: String): Boolean {
        if (phoneNumber.isBlank()) {
            Log.w("CaregiverSmsHelper", "Caregiver phone number is blank. SMS skipped.")
            return false
        }

        if (message.isBlank()) {
            Log.w("CaregiverSmsHelper", "SMS message body is blank. SMS skipped.")
            return false
        }

        // Permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CaregiverSmsHelper", "SEND_SMS permission is not granted. SMS skipped.")
            return false
        }

        // Duplicate protection check
        if (eventId.isNotBlank() && processedEventIds.contains(eventId)) {
            Log.d("CaregiverSmsHelper", "Duplicate SMS attempt blocked for event ID: $eventId")
            return false
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }

            // Mark as processed ONLY after SmsManager completes dispatch successfully without throwing an exception
            if (eventId.isNotBlank()) {
                if (processedEventIds.size > 500) {
                    processedEventIds.clear()
                }
                processedEventIds.add(eventId)
            }

            Log.d("CaregiverSmsHelper", "Caregiver SMS notification sent successfully.")
            true
        } catch (e: Exception) {
            Log.e("CaregiverSmsHelper", "Failed to send caregiver SMS: ${e.message}", e)
            false
        }
    }
}
