package com.smartmedguardian.data.dao

import androidx.room.*
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.CaregiverInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface AdherenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdherenceRecord(record: AdherenceRecord)

    @Query("SELECT * FROM adherence_records WHERE medicineName = :name ORDER BY scheduledTime DESC")
    fun getAdherenceForMedicine(name: String): Flow<List<AdherenceRecord>>

    @Query("SELECT * FROM adherence_records WHERE medicineName = :name AND scheduledTime BETWEEN :startTime AND :endTime")
    suspend fun getAdherenceForMedicineInWindow(name: String, startTime: Long, endTime: Long): List<AdherenceRecord>

    @Query("SELECT * FROM adherence_records WHERE medicineName IN (:names) AND scheduledTime BETWEEN :startTime AND :endTime")
    suspend fun getAdherenceForCanonicalNamesInWindow(names: List<String>, startTime: Long, endTime: Long): List<AdherenceRecord>

    @Query("SELECT * FROM adherence_records WHERE scheduledDoseId = :doseId LIMIT 1")
    suspend fun getRecordByDoseId(doseId: String): AdherenceRecord?

    @Update
    suspend fun updateAdherenceRecord(record: AdherenceRecord)

    @Query("SELECT * FROM adherence_records ORDER BY scheduledTime DESC")
    fun getAllAdherenceRecords(): Flow<List<AdherenceRecord>>

    @Query("SELECT * FROM adherence_records WHERE scheduledTime >= :startTime")
    fun getAdherenceSince(startTime: Long): Flow<List<AdherenceRecord>>

    @Query("DELETE FROM adherence_records")
    suspend fun deleteAllAdherenceRecords()

    // Caregiver Info
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaregiver(info: CaregiverInfo)

    @Query("SELECT * FROM caregiver_info WHERE id = 1")
    fun getCaregiver(): Flow<CaregiverInfo?>

    @Query("DELETE FROM caregiver_info")
    suspend fun deleteCaregiver()
}
