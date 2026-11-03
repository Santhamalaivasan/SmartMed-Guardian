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

private val MIGRATION_10_11 = object : Migration(10, 11) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            """
            INSERT OR REPLACE INTO medicine_info (
                medicineName, genericName, brandName, category, strength, dosageForm, manufacturer, uses, commonSideEffects, seriousSideEffects, storageInstructions, beforeAfterFood, pregnancyWarning, childrenWarning, elderlyWarning, maxDailyDose, barcode
            ) VALUES 
            ('Adol', 'Paracetamol (Acetaminophen)', 'Adol, Panadol', 'Analgesic, Antipyretic', '500mg', 'Tablet, Syrup, Suppository', 'Julphar (Gulf Pharmaceutical Industries)', 'Relief of mild-to-moderate pain (headache, toothache, muscle ache) and fever reduction.', 'Rarely, nausea or allergic rash.', 'Hepatotoxicity (liver damage) with acute overdose; severe cutaneous adverse reactions (Stevens-Johnson syndrome).', 'Store below 30°C in a dry place away from direct sunlight.', 'Can be taken with or without food.', 'Generally considered safe at recommended therapeutic doses under medical supervision; low clinical risk.', 'Use child-specific pediatric liquid or suppository formulations with weight-based dosing.', 'Generally safe; monitor total daily dosage in patients with hepatic impairment.', '4000mg (8 tablets of 500mg) per 24 hours.', '6291003001015'),
            ('Augmentin', 'Amoxicillin + Clavulanic Acid', 'Augmentin, Curam', 'Antibiotic (Penicillin + Beta-lactamase inhibitor)', '625mg (500mg/125mg), 1000mg (875mg/125mg)', 'Film-coated Tablet, Oral Suspension', 'GlaxoSmithKline (GSK)', 'Treatment of bacterial infections including respiratory tract infections, otitis media, sinusitis, skin and urinary tract infections.', 'Diarrhea, nausea, vomiting, abdominal discomfort, oral candidiasis.', 'Severe allergic reactions (anaphylaxis), Clostridioides difficile-associated diarrhea, cholestatic jaundice, hepatitis.', 'Store tablets below 25°C in a dry place. Refrigerate reconstituted oral suspension (2°C–8°C) and use within 7–10 days.', 'Take at the start of a meal to enhance absorption and reduce gastrointestinal upset.', 'Use during pregnancy only if clearly needed and prescribed by a physician; low risk established in human studies.', 'Dosage calculated strictly based on body weight using oral suspension.', 'Monitor renal function; dosage adjustment required in severe renal impairment.', '2000mg Amoxicillin component per day as directed by physician.', '5000158062501'),
            ('Brufen', 'Ibuprofen', 'Brufen, Advil, Motrin', 'NSAID, Analgesic, Antipyretic', '400mg, 600mg', 'Film-coated Tablet, Syrup, Effervescent Granules', 'Abbott Laboratories', 'Relief of mild-to-moderate pain, dysmenorrhea, dental pain, fever, rheumatoid arthritis, and osteoarthritis.', 'Heartburn, dyspepsia, nausea, abdominal discomfort, dizziness.', 'Gastrointestinal ulceration and bleeding, cardiovascular thrombotic risks, acute kidney injury.', 'Store below 25°C in a cool, dry place.', 'Take with or after food or milk to minimize stomach irritation.', 'Contraindicated in third trimester due to risk of premature closure of ductus arteriosus and oligohydramnios.', 'Use pediatric suspension; not recommended under 3 months or <5kg without medical advice.', 'Higher risk of serious GI bleeding and renal complications; use lowest effective dose.', '2400mg per 24 hours under medical supervision (1200mg OTC limit).', '5011402004008'),
            ('Cataflam', 'Diclofenac Potassium', 'Cataflam, Voltaren', 'NSAID (Fast-acting)', '50mg', 'Sugar-coated Tablet, Dispersible Tablet', 'Novartis / Sandoz', 'Fast-acting relief of acute pain and inflammation in conditions like dysmenorrhea, migraine, post-traumatic pain, and dental pain.', 'Epigastric pain, nausea, diarrhea, abdominal cramps, headache.', 'Serious GI bleeding/ulceration, elevated liver enzymes, myocardial infarction risk, acute kidney injury.', 'Store below 30°C in original package to protect from moisture and light.', 'Take before meals or on an empty stomach with fluid for rapid pain relief.', 'Contraindicated in third trimester; avoid during earlier pregnancy unless explicitly prescribed by a physician.', 'Not recommended for children under 14 years of age.', 'Use lowest effective dose due to elevated GI and cardiovascular risk.', '150mg per 24 hours (typically 50mg 2-3 times daily).', '7680512340501'),
            ('Claritine', 'Loratadine', 'Claritine, Claritin', 'Second-Generation Antihistamine (Non-sedating)', '10mg', 'Tablet, Syrup', 'Bayer Healthcare', 'Symptomatic relief of allergic rhinitis (sneezing, runny nose, itching) and chronic hives (urticaria).', 'Headache, fatigue, dry mouth, mild drowsiness (rare).', 'Rare severe hypersensitivity reactions (anaphylaxis, rash), palpitations, liver dysfunction.', 'Store between 15°C and 30°C in a dry place.', 'Can be taken with or without food.', 'Low clinical risk during pregnancy; consult doctor before use.', 'Tablets for children >6 years (>30kg); use syrup for younger children (2–6 years).', 'Generally safe; adjust dosage in severe hepatic impairment.', '10mg once daily.', '0300850550102'),
            ('Dolo-650', 'Paracetamol (Acetaminophen)', 'Dolo-650, Dolo, Dolo650', 'Analgesic, Antipyretic', '650mg', 'Tablet', 'Micro Labs Limited', 'Treatment of moderate-to-severe fever (viral, post-vaccination) and relief of body aches, toothache, and headache.', 'Nausea, mild stomach discomfort, rare allergic rash.', 'Severe hepatotoxicity / liver damage with acute overdose or chronic overuse.', 'Store in a cool, dry place below 30°C.', 'Take after meals or with water to minimize gastric discomfort.', 'Generally safe at therapeutic doses when prescribed by a physician.', '650mg strength is for adults and adolescents >50kg; not for young children.', 'Maintain minimum 4–6 hour dosing intervals; do not exceed daily limit.', '4000mg (maximum 6 tablets of 650mg) per 24 hours.', '8901148006501'),
            ('Flagyl', 'Metronidazole', 'Flagyl', 'Antimicrobial, Antiprotozoal, Antibacterial', '200mg, 400mg, 500mg', 'Film-coated Tablet, Oral Suspension, Infusion', 'Sanofi', 'Treatment of anaerobic bacterial infections, amebiasis, giardiasis, trichomoniasis, and dental infections.', 'Metallic taste in mouth, nausea, abdominal cramps, dark urine.', 'Peripheral neuropathy, encephalopathy, seizures, severe disulfiram-like reaction with alcohol.', 'Store below 30°C in a light-resistant container.', 'Take during or immediately after meals to reduce stomach irritation.', 'Contraindicated in first trimester of pregnancy due to organogenesis risk.', 'Pediatric dosage calculated strictly on body weight (mg/kg).', 'Monitor closely for neurotoxicity due to decreased drug clearance.', '2000mg per 24 hours depending on clinical indication.', '3664798004001'),
            ('Glucophage', 'Metformin Hydrochloride', 'Glucophage, Glucophage XR', 'Antidiabetic Agent (Biguanide)', '500mg, 850mg, 1000mg', 'Film-coated Tablet, Extended-Release Tablet (XR)', 'Merck KGaA / Lipha', 'First-line treatment for Type 2 Diabetes Mellitus to improve glycemic control.', 'Diarrhea, nausea, flatulence, stomach cramps, Vitamin B12 deficiency with chronic use.', 'Lactic acidosis (rare but life-threatening metabolic emergency). Contraindicated if eGFR <30 mL/min.', 'Store at room temperature (15°C–30°C) away from heat and moisture.', 'Take during or immediately after meals to lower GI side effects. Swallow XR whole.', 'Acceptable during pregnancy under specialist endocrinology supervision.', 'Approved for children 10 years and older with Type 2 diabetes.', 'Regularly monitor renal function (eGFR); contraindicated if eGFR <30 mL/min.', '2550mg per 24 hours for IR (2000mg for XR).', '3400932822105'),
            ('Paramol', 'Paracetamol (Acetaminophen)', 'Paramol', 'Analgesic, Antipyretic', '500mg', 'Tablet, Effervescent Tablet, Syrup', 'Amoun Pharmaceutical Co.', 'Relief of mild-to-moderate pain (headache, dysmenorrhea, muscle ache) and fever management.', 'Rare when used as directed; occasional rash.', 'Severe hepatotoxicity with acute overdose; severe skin hypersensitivity.', 'Store in a cool, dry place below 30°C.', 'Can be taken with or after food with water.', 'Safe at standard therapeutic doses during pregnancy under medical advice.', 'Use pediatric suspension for children under 12 years.', 'Safe; monitor hepatic function and avoid concomitant alcohol consumption.', '4000mg (8 tablets of 500mg) per 24 hours.', '6221034005001'),
            ('Rennie', 'Calcium Carbonate + Magnesium Carbonate', 'Rennie Peppermint, Rennie Spearmint', 'Antacid', '680mg Calcium Carbonate + 80mg Magnesium Carbonate', 'Chewable Tablet', 'Bayer Healthcare', 'Fast relief from heartburn, acid indigestion, hyperacidity, and gastric discomfort.', 'Constipation (from calcium), mild laxative effect (from magnesium), belching.', 'Hypercalcemia, alkalosis, rebound hyperacidity with chronic high-dose use.', 'Store in original package below 25°C in a dry place.', 'Chew 1–2 tablets after meals or at bedtime, or when symptoms occur.', 'Safe during pregnancy and lactation if taken as recommended.', 'Not recommended for children under 12 years without medical advice.', 'Use caution in patients with impaired renal function or hypercalcemia.', 'Maximum 11 chewable tablets in 24 hours; limit continuous use to 14 days.', '5000167006800'),
            ('Zantac', 'Famotidine (Zantac 360 formulation; historical Ranitidine withdrawn globally due to NDMA safety concerns)', 'Zantac 360, Zantac', 'H2-Receptor Antagonist (H2 Blocker)', '10mg, 20mg', 'Film-coated Tablet', 'Sanofi', 'Prevention and treatment of heartburn, acid indigestion, and GERD.', 'Headache, dizziness, constipation, diarrhea.', 'Rare CNS effects (confusion in elderly/renal impairment), blood dyscrasias, QT prolongation.', 'Store below 25°C in a dry place protected from light.', 'Take 15–60 minutes before eating food or drinking beverages that cause heartburn.', 'Use during pregnancy only if clearly needed and prescribed by physician.', 'Approved for pediatric use under medical supervision with weight-based dosing.', 'Dose reduction required in moderate-to-severe renal impairment.', '40mg per 24 hours for OTC heartburn relief.', '0411670150101'),
            ('Zyrtec', 'Cetirizine Dihydrochloride', 'Zyrtec, Reactine', 'Second-Generation Antihistamine', '10mg', 'Film-coated Tablet, Oral Solution, Syrup', 'UCB Pharma / Johnson & Johnson', '24-hour relief of seasonal/perennial allergic rhinitis (sneezing, runny nose) and hives (urticaria).', 'Drowsiness, dry mouth, fatigue, dizziness.', 'Severe allergic reactions (angioedema, anaphylaxis), hepatitis, urinary retention.', 'Store below 30°C in original package.', 'Can be taken with or without food.', 'Use during pregnancy only if potential clinical benefit justifies potential risk to fetus.', 'Use oral solution for children 2–11 years; 10mg tablet for >6 years.', 'Start at lower dose (5mg) in elderly or renal impairment.', '10mg once daily.', '7047010100103')
            """.trimIndent()
        )

        database.execSQL(
            """
            INSERT OR REPLACE INTO drug_interactions (id, medicineA, medicineB, severity, reason, recommendation) VALUES
            (1, 'Brufen', 'Aspirin', 'High', 'Increased risk of severe gastrointestinal ulceration/bleeding and reduced cardioprotective effect of Aspirin.', 'Avoid concurrent use or consult a physician for cardiovascular antiplatelet protection.'),
            (2, 'Cataflam', 'Aspirin', 'High', 'Additive inhibition of cyclooxygenase leading to high risk of gastrointestinal mucosal damage and renal toxicity.', 'Avoid combination of multiple oral NSAIDs.'),
            (3, 'Flagyl', 'Alcohol', 'High', 'Disulfiram-like reaction causing severe flushing, throbbing headache, nausea, vomiting, and tachycardia.', 'Strictly avoid alcohol during Flagyl treatment and for at least 48 hours after the last dose.'),
            (4, 'Dolo-650', 'Alcohol', 'Moderate', 'Increased risk of liver toxicity due to cumulative depletion of hepatic glutathione.', 'Limit alcohol consumption while taking Paracetamol.'),
            (5, 'Adol', 'Alcohol', 'Moderate', 'Increased risk of liver toxicity due to cumulative depletion of hepatic glutathione.', 'Limit alcohol consumption while taking Paracetamol.'),
            (6, 'Rennie', 'Augmentin', 'Moderate', 'Antacids containing divalent cations (calcium/magnesium) can reduce gastrointestinal absorption of oral antibiotics.', 'Separate doses by at least 2 hours')
            """.trimIndent()
        )
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE adherence_records ADD COLUMN scheduledDoseId TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE adherence_records ADD COLUMN caregiverNotified INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE adherence_records ADD COLUMN notificationStatus TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE adherence_records ADD COLUMN notificationTimestamp INTEGER DEFAULT NULL")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
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
            db.execSQL("INSERT OR IGNORE INTO medicine_conditions (medicineName, conditionName) VALUES ('$med', '$cond')")
        }
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
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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

                                val condDao = it.medicineConditionDao()
                                condDao.insertMedicineConditions(listOf(
                                    com.smartmedguardian.data.entity.MedicineCondition("Glucophage", "Diabetes"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Amlodipine", "Blood Pressure"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Losartan", "Blood Pressure"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Adol", "Fever"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Dolo-650", "Fever"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Paramol", "Fever"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Brufen", "Fever"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Cataflam", "Fever"),
                                    com.smartmedguardian.data.entity.MedicineCondition("Levothyroxine", "Thyroid")
                                ))
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
    fun provideMedicineConditionDao(
        database: SmartMedDatabase
    ): MedicineConditionDao {
        return database.medicineConditionDao()
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
        medicineConditionDao: MedicineConditionDao,
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
            medicineConditionDao,
            verificationManager,
            securityHelper,
            smsHelper,
            auth,
            firestore
        )
    }
}
