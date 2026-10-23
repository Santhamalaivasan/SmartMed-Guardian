package com.smartmedguardian.data.dao

import androidx.room.*
import com.smartmedguardian.data.entity.CaregiverAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface CaregiverAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: CaregiverAlert)

    @Update
    suspend fun updateAlert(alert: CaregiverAlert)

    @Query("SELECT * FROM caregiver_alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<CaregiverAlert>>

    @Query("SELECT * FROM caregiver_alerts WHERE alertId = :alertId")
    suspend fun getAlertById(alertId: String): CaregiverAlert?

    @Query("SELECT * FROM caregiver_alerts WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun getAlertsForPatient(patientId: String): Flow<List<CaregiverAlert>>
}
