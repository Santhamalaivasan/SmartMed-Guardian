package com.smartmedguardian.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.smartmedguardian.data.SmartMedDatabase
import com.smartmedguardian.data.dao.*
import com.smartmedguardian.data.entity.DrugInteraction
import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.ml.MedicineClassifier
import com.smartmedguardian.ml.MedicineEmbeddingHelper
import com.smartmedguardian.ml.MedicineReferenceMatcher
import com.smartmedguardian.ml.TFLiteMedicineClassifier
import com.smartmedguardian.ml.TensorFlowHelper
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.repository.MedicineRepositoryImpl
import com.smartmedguardian.util.BarcodeScannerHelper
import com.smartmedguardian.util.CaregiverSmsHelper
import com.smartmedguardian.util.MedicineVerificationManager
import com.smartmedguardian.util.SecurityHelper
import com.smartmedguardian.util.TextRecognitionHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton


data class MedicineData(
    val medicines: List<MedicineInfo>,
    val interactions: List<DrugInteraction>
)


private val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS medicine_reference_images (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                prescriptionId INTEGER NOT NULL,
                imagePath TEXT NOT NULL,
                FOREIGN KEY(prescriptionId)
                    REFERENCES prescriptions(id)
                    ON UPDATE NO ACTION
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_medicine_reference_images_prescriptionId
            ON medicine_reference_images(prescriptionId)
            """.trimIndent()
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL("ALTER TABLE medicine_reference_images ADD COLUMN embedding TEXT DEFAULT NULL")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL("ALTER TABLE prescriptions ADD COLUMN morningTime TEXT NOT NULL DEFAULT '08:00'")
        database.execSQL("ALTER TABLE prescriptions ADD COLUMN afternoonTime TEXT NOT NULL DEFAULT '13:00'")
        database.execSQL("ALTER TABLE prescriptions ADD COLUMN eveningTime TEXT NOT NULL DEFAULT '20:00'")
        database.execSQL("ALTER TABLE prescriptions ADD COLUMN nightTime TEXT NOT NULL DEFAULT '22:00'")
        database.execSQL("ALTER TABLE prescriptions ADD COLUMN remindersEnabled INTEGER NOT NULL DEFAULT 1")
    }
}


private val MIGRATION_8_9 = object : Migration(8, 9) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL("ALTER TABLE caregiver_info ADD COLUMN caregiverFcmToken TEXT DEFAULT NULL")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS caregiver_alerts (
                alertId TEXT PRIMARY KEY NOT NULL,
                patientId TEXT NOT NULL,
                caregiverId TEXT NOT NULL,
                medicineName TEXT NOT NULL,
                eventType TEXT NOT NULL,
                message TEXT NOT NULL,
                scheduledTime INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                sentAt INTEGER,
                status TEXT NOT NULL,
                acknowledged INTEGER NOT NULL DEFAULT 0,
                acknowledgedAt INTEGER
            )
            """.trimIndent()
        )
    }
}


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SmartMedDatabase {

        var database: SmartMedDatabase? = null

        database = Room.databaseBuilder(
            context,
            SmartMedDatabase::class.java,
            "smart_med_guardian_db"
        )
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .addCallback(object : RoomDatabase.Callback() {

                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)

                    CoroutineScope(Dispatchers.IO).launch {

                        try {

                            val jsonString =
                                context.assets
                                    .open("medicine_data.json")
                                    .bufferedReader()
                                    .use { it.readText() }

                            val data = Gson().fromJson(
                                jsonString,
                                MedicineData::class.java
                            )

                            database?.let {

                                val dao = it.safetyDao()

                                dao.insertMedicineInfo(
                                    data.medicines
                                )

                                dao.insertInteractions(
                                    data.interactions
                                )
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            })
            .build()

        return database
    }


    @Provides
    fun providePrescriptionDao(
        database: SmartMedDatabase
    ): PrescriptionDao {
        return database.prescriptionDao()
    }


    @Provides
    fun provideMedicineReferenceImageDao(
        database: SmartMedDatabase
    ): MedicineReferenceImageDao {
        return database.medicineReferenceImageDao()
    }


    @Provides
    fun provideMedicineHistoryDao(
        database: SmartMedDatabase
    ): MedicineHistoryDao {
        return database.medicineHistoryDao()
    }


    @Provides
    fun provideSafetyDao(
        database: SmartMedDatabase
    ): SafetyDao {
        return database.safetyDao()
    }


    @Provides
    fun provideAdherenceDao(
        database: SmartMedDatabase
    ): AdherenceDao {
        return database.adherenceDao()
    }


    @Provides
    fun provideCaregiverAlertDao(
        database: SmartMedDatabase
    ): CaregiverAlertDao {
        return database.caregiverAlertDao()
    }


    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }


    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return Firebase.firestore
    }


    @Provides
    @Singleton
    fun provideTensorFlowHelper(
        @ApplicationContext context: Context
    ): TensorFlowHelper {
        return TensorFlowHelper(context)
    }


    @Provides
    @Singleton
    fun provideMedicineClassifier(
        helper: TensorFlowHelper
    ): MedicineClassifier {
        return TFLiteMedicineClassifier(helper)
    }


    @Provides
    @Singleton
    fun provideMedicineEmbeddingHelper(
        @ApplicationContext context: Context
    ): MedicineEmbeddingHelper {
        return MedicineEmbeddingHelper(context)
    }


    @Provides
    @Singleton
    fun provideMedicineReferenceMatcher(): MedicineReferenceMatcher {
        return MedicineReferenceMatcher()
    }


    @Provides
    @Singleton
    fun provideTextRecognitionHelper(): TextRecognitionHelper {
        return TextRecognitionHelper()
    }


    @Provides
    @Singleton
    fun provideBarcodeScannerHelper(): BarcodeScannerHelper {
        return BarcodeScannerHelper()
    }


    @Provides
    @Singleton
    fun provideMedicineRepository(
        @ApplicationContext context: Context,
        classifier: MedicineClassifier,
        embeddingHelper: MedicineEmbeddingHelper,
        referenceMatcher: MedicineReferenceMatcher,
        prescriptionDao: PrescriptionDao,
        medicineReferenceImageDao: MedicineReferenceImageDao,
        historyDao: MedicineHistoryDao,
        safetyDao: SafetyDao,
        adherenceDao: AdherenceDao,
        caregiverAlertDao: CaregiverAlertDao,
        verificationManager: MedicineVerificationManager,
        securityHelper: SecurityHelper,
        smsHelper: CaregiverSmsHelper,
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): MedicineRepository {

        return MedicineRepositoryImpl(
            context,
            classifier,
            embeddingHelper,
            referenceMatcher,
            prescriptionDao,
            medicineReferenceImageDao,
            historyDao,
            safetyDao,
            adherenceDao,
            caregiverAlertDao,
            verificationManager,
            securityHelper,
            smsHelper,
            auth,
            firestore
        )
    }
}
