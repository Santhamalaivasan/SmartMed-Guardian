package com.smartmedguardian.data.dao

import androidx.room.*
import com.smartmedguardian.data.entity.DrugInteraction
import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.data.entity.UserAllergy
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyDao {
    // Medicine Info
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineInfo(info: List<MedicineInfo>)

    @Query("SELECT * FROM medicine_info WHERE medicineName = :name OR (medicineName = 'Dolo-650' AND (:name = 'Dolo650' OR :name = 'Dolo-650')) OR brandName LIKE '%' || :name || '%' LIMIT 1")
    suspend fun getMedicineInfoByName(name: String): MedicineInfo?

    @Query("SELECT * FROM medicine_info WHERE barcode = :barcode LIMIT 1")
    suspend fun getMedicineInfoByBarcode(barcode: String): MedicineInfo?

    @Query("SELECT * FROM medicine_info WHERE medicineName LIKE '%' || :query || '%' OR genericName LIKE '%' || :query || '%'")
    fun searchMedicines(query: String): Flow<List<MedicineInfo>>

    // Interactions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractions(interactions: List<DrugInteraction>)

    @Query("SELECT * FROM drug_interactions WHERE (medicineA = :medA AND medicineB = :medB) OR (medicineA = :medB AND medicineB = :medA) LIMIT 1")
    suspend fun getInteraction(medA: String, medB: String): DrugInteraction?

    // Allergies
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllergy(allergy: UserAllergy)

    @Query("SELECT * FROM user_allergies")
    fun getAllAllergies(): Flow<List<UserAllergy>>

    @Update
    suspend fun updateAllergy(allergy: UserAllergy)

    @Delete
    suspend fun deleteAllergy(allergy: UserAllergy)

    @Query("DELETE FROM user_allergies")
    suspend fun deleteAllAllergies()
}
