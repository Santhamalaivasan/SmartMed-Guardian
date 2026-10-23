package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adherence_records")
data class AdherenceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineName: String,
    val scheduledTime: Long,
    val actualTime: Long?,
    val status: String, // "TAKEN", "LATE", "MISSED", "SKIPPED"
    val delayMinutes: Int = 0
)
