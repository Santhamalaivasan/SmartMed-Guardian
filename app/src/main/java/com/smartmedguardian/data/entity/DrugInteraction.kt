package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drug_interactions")
data class DrugInteraction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineA: String,
    val medicineB: String,
    val severity: String, // "Low", "Moderate", "High"
    val reason: String,
    val recommendation: String
)
