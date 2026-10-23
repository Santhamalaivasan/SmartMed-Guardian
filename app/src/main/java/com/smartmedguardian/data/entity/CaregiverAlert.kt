package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caregiver_alerts")
data class CaregiverAlert(
    @PrimaryKey
    val alertId: String = "",
    val patientId: String = "",
    val caregiverId: String = "",
    val medicineName: String = "",
    val eventType: String = "MISSED_DOSE", // MISSED_DOSE, WRONG_MEDICINE, DUPLICATE_DOSE, SAFETY_WARNING
    val message: String = "",
    val scheduledTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val status: String = "CREATED", // CREATED, SENT, ACKNOWLEDGED, FAILED
    val acknowledged: Boolean = false,
    val acknowledgedAt: Long? = null
)
