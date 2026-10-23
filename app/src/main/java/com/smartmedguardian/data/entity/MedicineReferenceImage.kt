package com.smartmedguardian.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicine_reference_images",
    foreignKeys = [
        ForeignKey(
            entity = Prescription::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["prescriptionId"])
    ]
)
data class MedicineReferenceImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val prescriptionId: Long,

    val imagePath: String,

    val embedding: String? = null
)
