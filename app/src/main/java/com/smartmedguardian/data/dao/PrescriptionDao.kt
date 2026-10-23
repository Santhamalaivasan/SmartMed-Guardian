package com.smartmedguardian.data.dao

import androidx.room.*
import com.smartmedguardian.data.entity.Prescription
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: Prescription): Long

    @Update
    suspend fun updatePrescription(prescription: Prescription)

    @Delete
    suspend fun deletePrescription(prescription: Prescription)

    @Query("SELECT * FROM prescriptions ORDER BY createdAt DESC")
    fun getAllPrescriptions(): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions WHERE medicineName = :name LIMIT 1")
    suspend fun getPrescriptionByName(name: String): Prescription?
}
