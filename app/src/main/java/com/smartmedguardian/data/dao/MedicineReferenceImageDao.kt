package com.smartmedguardian.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.smartmedguardian.data.entity.MedicineReferenceImage
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineReferenceImageDao {

    @Insert
    suspend fun insertReferenceImage(
        image: MedicineReferenceImage
    ): Long

    @Insert
    suspend fun insertReferenceImages(
        images: List<MedicineReferenceImage>
    )

    @Update
    suspend fun updateReferenceImage(
        image: MedicineReferenceImage
    )

    @Delete
    suspend fun deleteReferenceImage(
        image: MedicineReferenceImage
    )

    @Query("""
        SELECT * FROM medicine_reference_images
        WHERE prescriptionId = :prescriptionId
        ORDER BY id ASC
    """)
    fun getImagesForPrescription(
        prescriptionId: Long
    ): Flow<List<MedicineReferenceImage>>

    @Query("""
        SELECT * FROM medicine_reference_images
        WHERE prescriptionId = :prescriptionId
        ORDER BY id ASC
    """)
    suspend fun getImagesForPrescriptionOnce(
        prescriptionId: Long
    ): List<MedicineReferenceImage>

    @Query("""
        SELECT * FROM medicine_reference_images
        ORDER BY id ASC
    """)
    suspend fun getAllReferenceImagesOnce(): List<MedicineReferenceImage>

    @Query("""
        DELETE FROM medicine_reference_images
        WHERE prescriptionId = :prescriptionId
    """)
    suspend fun deleteImagesForPrescription(
        prescriptionId: Long
    )

    @Query("DELETE FROM medicine_reference_images")
    suspend fun deleteAllReferenceImages()
}
