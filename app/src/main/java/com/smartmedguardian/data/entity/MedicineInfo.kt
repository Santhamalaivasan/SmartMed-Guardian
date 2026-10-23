package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_info")
data class MedicineInfo(
    @PrimaryKey
    val medicineName: String,
    val genericName: String,
    val brandName: String,
    val category: String,
    val strength: String,
    val dosageForm: String,
    val manufacturer: String,
    val uses: String,
    val commonSideEffects: String,
    val seriousSideEffects: String,
    val storageInstructions: String,
    val beforeAfterFood: String,
    val pregnancyWarning: String,
    val childrenWarning: String,
    val elderlyWarning: String,
    val maxDailyDose: String,
    val barcode: String? = null
)
