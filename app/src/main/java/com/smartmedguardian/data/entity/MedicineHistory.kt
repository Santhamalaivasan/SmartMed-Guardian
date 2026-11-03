package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_history")
data class MedicineHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String? = null,
    val prescriptionId: Long? = null,
    val detectedMedicineName: String = "",
    val confidence: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val verificationStatus: String = "", // "CORRECT_MEDICINE", "MEDICINE_NOT_FOUND", "UNABLE_TO_VERIFY"
    val imagePath: String? = null
)
