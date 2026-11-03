package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_allergies")
data class UserAllergy(
    @PrimaryKey
    val allergen: String = "", // e.g., "Penicillin", "Sulfa drugs", "NSAIDs"
    val isEnabled: Boolean = true
)
