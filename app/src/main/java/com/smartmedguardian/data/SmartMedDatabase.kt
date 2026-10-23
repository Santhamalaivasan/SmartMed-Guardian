package com.smartmedguardian.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smartmedguardian.data.dao.AdherenceDao
import com.smartmedguardian.data.dao.CaregiverAlertDao
import com.smartmedguardian.data.dao.MedicineHistoryDao
import com.smartmedguardian.data.dao.MedicineReferenceImageDao
import com.smartmedguardian.data.dao.PrescriptionDao
import com.smartmedguardian.data.dao.SafetyDao
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.CaregiverAlert
import com.smartmedguardian.data.entity.CaregiverInfo
import com.smartmedguardian.data.entity.DrugInteraction
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
        CaregiverAlert::class
    ],
    version = 10,
    exportSchema = false
)
abstract class SmartMedDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao

    abstract fun medicineReferenceImageDao(): MedicineReferenceImageDao

    abstract fun medicineHistoryDao(): MedicineHistoryDao

    abstract fun safetyDao(): SafetyDao

    abstract fun adherenceDao(): AdherenceDao

    abstract fun caregiverAlertDao(): CaregiverAlertDao
}
