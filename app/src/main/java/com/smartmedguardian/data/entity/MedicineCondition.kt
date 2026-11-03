package com.smartmedguardian.data.entity

import androidx.room.Entity

@Entity(
    tableName = "medicine_conditions",
    primaryKeys = ["medicineName", "conditionName"]
)
data class MedicineCondition(
    val medicineName: String,
    val conditionName: String // "Diabetes", "Blood Pressure", "Fever", "Thyroid"
)
