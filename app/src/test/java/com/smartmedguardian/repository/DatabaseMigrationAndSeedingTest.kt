package com.smartmedguardian.repository

import com.google.gson.Gson
import com.smartmedguardian.di.MedicineData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseMigrationAndSeedingTest {

    @Test
    fun verifyMedicineDataJsonParsingAndStructure() {
        val jsonFile = File("src/main/assets/medicine_data.json")
        assertTrue("medicine_data.json must exist in assets", jsonFile.exists())

        val jsonString = jsonFile.readText()
        val data = Gson().fromJson(jsonString, MedicineData::class.java)

        assertNotNull("Parsed MedicineData must not be null", data)
        assertNotNull("Medicines list must not be null", data.medicines)
        assertNotNull("Interactions list must not be null", data.interactions)

        // Check exactly 15 primary medicine entries
        assertEquals("Should contain exactly 15 primary medicine profiles", 15, data.medicines.size)

        val supportedNames = listOf(
            "Adol", "Augmentin", "Brufen", "Cataflam", "Claritine",
            "Dolo-650", "Flagyl", "Glucophage", "Paramol", "Rennie",
            "Zantac", "Zyrtec", "Amlodipine", "Losartan", "Levothyroxine"
        )

        for (name in supportedNames) {
            val med = data.medicines.find { it.medicineName.equals(name, ignoreCase = true) }
            assertNotNull("Medicine '$name' must exist in medicine_data.json", med)
            assertTrue("Generic name for '$name' must not be blank", med!!.genericName.isNotBlank())
            assertTrue("Category for '$name' must not be blank", med.category.isNotBlank())
            assertTrue("Strength for '$name' must not be blank", med.strength.isNotBlank())
            assertTrue("Uses for '$name' must not be blank", med.uses.isNotBlank())
            assertTrue("Max daily dose for '$name' must not be blank", med.maxDailyDose.isNotBlank())
        }

        // Verify Dolo650 is treated as brand alias in Dolo-650 entry, not as duplicate row
        val duplicateDolo = data.medicines.filter { it.medicineName.equals("Dolo650", ignoreCase = true) }
        assertEquals("Dolo650 should not exist as a separate primary key row", 0, duplicateDolo.size)

        val dolo650Entry = data.medicines.find { it.medicineName == "Dolo-650" }
        assertNotNull("Canonical Dolo-650 entry must exist", dolo650Entry)
        assertTrue("Dolo-650 brandName should contain alias Dolo650", dolo650Entry!!.brandName.contains("Dolo650"))
    }

    @Test
    fun verifyInteractionsDataset() {
        val jsonFile = File("src/main/assets/medicine_data.json")
        val jsonString = jsonFile.readText()
        val data = Gson().fromJson(jsonString, MedicineData::class.java)

        assertTrue("Interactions list should contain pairwise drug interactions", data.interactions.isNotEmpty())
        
        val brufenAspirin = data.interactions.find { 
            (it.medicineA == "Brufen" && it.medicineB == "Aspirin") || (it.medicineA == "Aspirin" && it.medicineB == "Brufen") 
        }
        assertNotNull("Brufen + Aspirin interaction must exist", brufenAspirin)
        assertEquals("High", brufenAspirin!!.severity)

        val flagylAlcohol = data.interactions.find { 
            (it.medicineA == "Flagyl" && it.medicineB == "Alcohol") || (it.medicineA == "Alcohol" && it.medicineB == "Flagyl") 
        }
        assertNotNull("Flagyl + Alcohol interaction must exist", flagylAlcohol)
        assertEquals("High", flagylAlcohol!!.severity)
    }

    @Test
    fun verifyFamotidineZantacFormulationSpecification() {
        val jsonFile = File("src/main/assets/medicine_data.json")
        val jsonString = jsonFile.readText()
        val data = Gson().fromJson(jsonString, MedicineData::class.java)

        val zantac = data.medicines.find { it.medicineName == "Zantac" }
        assertNotNull("Zantac entry must exist", zantac)
        assertTrue(
            "Zantac generic name must specify Famotidine Zantac 360 formulation",
            zantac!!.genericName.contains("Famotidine", ignoreCase = true)
        )
    }
}
