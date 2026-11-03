package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adherence_records")
data class AdherenceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineName: String = "",
    val scheduledTime: Long = 0L,
    val actualTime: Long? = null,
    val status: String = "", // "TAKEN", "LATE", "MISSED", "SKIPPED"
    val delayMinutes: Int = 0,
    val scheduledDoseId: String? = null,
    val caregiverNotified: Boolean = false,
    val notificationStatus: String? = null,
    val notificationTimestamp: Long? = null
)
