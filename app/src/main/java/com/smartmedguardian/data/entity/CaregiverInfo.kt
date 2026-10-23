package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caregiver_info")
data class CaregiverInfo(
    @PrimaryKey
    val id: Int = 1, // Single caregiver for now
    val encryptedName: String,
    val encryptedRelationship: String,
    val encryptedPhoneNumber: String,
    val encryptedEmail: String,
    val isEscalationEnabled: Boolean = true,
    val caregiverFcmToken: String? = null
)
