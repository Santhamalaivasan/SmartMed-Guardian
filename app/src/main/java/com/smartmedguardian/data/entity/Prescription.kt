package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prescriptions")
data class Prescription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineName: String,
    val dosage: String,
    val frequency: String,
    val instructions: String = "",
    val morning: Boolean = false,
    val afternoon: Boolean = false,
    val evening: Boolean = false,
    val night: Boolean = false,
    val morningTime: String = "08:00",
    val afternoonTime: String = "13:00",
    val eveningTime: String = "20:00",
    val nightTime: String = "22:00",
    val remindersEnabled: Boolean = true,
    val beforeFood: Boolean = false,
    val afterFood: Boolean = false,
    val startDate: Long,
    val endDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)
