package com.smartmedguardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmedguardian.data.entity.MedicineCondition
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineConditionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineCondition(mapping: MedicineCondition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineConditions(mappings: List<MedicineCondition>)

    @Query("SELECT medicineName FROM medicine_conditions WHERE conditionName = :conditionName")
    suspend fun getMedicinesForCondition(conditionName: String): List<String>

    @Query("SELECT conditionName FROM medicine_conditions WHERE medicineName = :medicineName")
    suspend fun getConditionsForMedicine(medicineName: String): List<String>

    @Query("SELECT * FROM medicine_conditions")
    fun getAllMappings(): Flow<List<MedicineCondition>>

    @Query("DELETE FROM medicine_conditions")
    suspend fun deleteAllMappings()
}
