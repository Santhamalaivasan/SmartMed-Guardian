package com.smartmedguardian.data.dao

import androidx.room.*
import com.smartmedguardian.data.entity.MedicineHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineHistoryDao {
    @Insert
    suspend fun insertHistory(history: MedicineHistory): Long

    @Query("SELECT * FROM medicine_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<MedicineHistory>>

    @Query("SELECT * FROM medicine_history WHERE verificationStatus IN (:statuses) ORDER BY timestamp DESC")
    suspend fun getRecentHistoryByStatus(statuses: List<String>): List<MedicineHistory>

    @Query("DELETE FROM medicine_history")
    suspend fun clearHistory()
}
