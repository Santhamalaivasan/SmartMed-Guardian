package com.smartmedguardian.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartmedguardian.data.dao.AdherenceDao
import com.smartmedguardian.data.dao.CaregiverAlertDao
import com.smartmedguardian.data.dao.MedicineConditionDao
import com.smartmedguardian.data.dao.MedicineHistoryDao
import com.smartmedguardian.data.dao.MedicineReferenceImageDao
import com.smartmedguardian.data.dao.PrescriptionDao
import com.smartmedguardian.data.dao.SafetyDao
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.CaregiverAlert
import com.smartmedguardian.data.entity.CaregiverInfo
import com.smartmedguardian.data.entity.DrugInteraction
import com.smartmedguardian.data.entity.MedicineCondition
import com.smartmedguardian.data.entity.MedicineHistory
import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.data.entity.MedicineReferenceImage
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.data.entity.UserAllergy

@Database(
    entities = [
        Prescription::class,
        MedicineReferenceImage::class,
        MedicineHistory::class,
        MedicineInfo::class,
        DrugInteraction::class,
        UserAllergy::class,
        AdherenceRecord::class,
        CaregiverInfo::class,
        CaregiverAlert::class,
        MedicineCondition::class
    ],
    version = 13,
    exportSchema = false
)
abstract class SmartMedDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao

    abstract fun medicineReferenceImageDao(): MedicineReferenceImageDao

    abstract fun medicineHistoryDao(): MedicineHistoryDao

    abstract fun safetyDao(): SafetyDao

    abstract fun adherenceDao(): AdherenceDao

    abstract fun caregiverAlertDao(): CaregiverAlertDao

    abstract fun medicineConditionDao(): MedicineConditionDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `medicine_conditions` (
                        `medicineName` TEXT NOT NULL,
                        `conditionName` TEXT NOT NULL,
                        PRIMARY KEY(`medicineName`, `conditionName`)
                    )
                """)
                val mappings = listOf(
                    "Glucophage" to "Diabetes",
                    "Amlodipine" to "Blood Pressure",
                    "Losartan" to "Blood Pressure",
                    "Adol" to "Fever",
                    "Dolo-650" to "Fever",
                    "Paramol" to "Fever",
                    "Brufen" to "Fever",
                    "Cataflam" to "Fever",
                    "Levothyroxine" to "Thyroid"
                )
                for ((med, cond) in mappings) {
                    database.execSQL("INSERT OR IGNORE INTO medicine_conditions (medicineName, conditionName) VALUES ('$med', '$cond')")
                }
            }
        }
    }
}
