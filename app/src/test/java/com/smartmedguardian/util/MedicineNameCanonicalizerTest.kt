package com.smartmedguardian.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineNameCanonicalizerTest {

    @Test
    fun testAll12CanonicalNames() {
        val canonicals = MedicineNameCanonicalizer.CANONICAL_NAMES
        assertEquals(12, canonicals.size)

        for (name in canonicals) {
            val result = MedicineNameCanonicalizer.canonicalize(name)
            assertEquals(name, result)
        }
    }

    @Test
    fun testExplicitDoloAliasesResolution() {
        assertEquals("Dolo-650", MedicineNameCanonicalizer.canonicalize("Dolo650"))
        assertEquals("Dolo-650", MedicineNameCanonicalizer.canonicalize("Dolo 650"))
        assertEquals("Dolo-650", MedicineNameCanonicalizer.canonicalize("Dolo-650"))
        assertEquals("Dolo-650", MedicineNameCanonicalizer.canonicalize("DOLO650"))
        assertEquals("Dolo-650", MedicineNameCanonicalizer.canonicalize("dolo 650"))
    }

    @Test
    fun testGenericIngredientNamesDoNotBecomeBrandNames() {
        assertEquals("amoxicillin", MedicineNameCanonicalizer.canonicalize("amoxicillin"))
        assertEquals("ibuprofen", MedicineNameCanonicalizer.canonicalize("ibuprofen"))
        assertEquals("diclofenac", MedicineNameCanonicalizer.canonicalize("diclofenac"))
        assertEquals("loratadine", MedicineNameCanonicalizer.canonicalize("loratadine"))
        assertEquals("metronidazole", MedicineNameCanonicalizer.canonicalize("metronidazole"))
        assertEquals("metformin", MedicineNameCanonicalizer.canonicalize("metformin"))
        assertEquals("famotidine", MedicineNameCanonicalizer.canonicalize("famotidine"))
        assertEquals("cetirizine", MedicineNameCanonicalizer.canonicalize("cetirizine"))
    }

    @Test
    fun testAmbiguousOrPartialTokensRemainUnresolved() {
        assertEquals("Dolo", MedicineNameCanonicalizer.canonicalize("Dolo"))
        assertEquals("Aug", MedicineNameCanonicalizer.canonicalize("Aug"))
        assertEquals("Bru", MedicineNameCanonicalizer.canonicalize("Bru"))
        assertEquals("Ren", MedicineNameCanonicalizer.canonicalize("Ren"))
    }

    @Test
    fun testRandomUnsupportedMedicinesRemainUnresolved() {
        assertEquals("Lipitor", MedicineNameCanonicalizer.canonicalize("Lipitor"))
        assertEquals("Omeprazole", MedicineNameCanonicalizer.canonicalize("Omeprazole"))
        assertEquals("Aspirin", MedicineNameCanonicalizer.canonicalize("Aspirin"))
    }

    @Test
    fun testCaseAndPunctuationNormalization() {
        assertEquals("Claritine", MedicineNameCanonicalizer.canonicalize("  cLaRiTiNe  "))
        assertEquals("Claritine", MedicineNameCanonicalizer.canonicalize("Claritin"))
        assertEquals("Glucophage", MedicineNameCanonicalizer.canonicalize("GLUCOPHAGE"))
        assertEquals("Cataflam", MedicineNameCanonicalizer.canonicalize("cataflam"))
    }

    @Test
    fun testCanonicalizationIdempotency() {
        for (name in MedicineNameCanonicalizer.CANONICAL_NAMES) {
            val firstPass = MedicineNameCanonicalizer.canonicalize(name)
            val secondPass = MedicineNameCanonicalizer.canonicalize(firstPass)
            assertEquals(firstPass, secondPass)
        }

        val doloFirst = MedicineNameCanonicalizer.canonicalize("Dolo650")
        val doloSecond = MedicineNameCanonicalizer.canonicalize(doloFirst)
        assertEquals("Dolo-650", doloSecond)
    }

    @Test
    fun testCnnModelLabelMapping() {
        val modelLabel = "Dolo650"
        val canonicalName = MedicineNameCanonicalizer.canonicalize(modelLabel)
        assertEquals("Dolo-650", canonicalName)
    }

    @Test
    fun testPrescriptionMatchingConsistency() {
        assertTrue(MedicineNameCanonicalizer.isSameMedicine("Dolo650", "Dolo-650"))
        assertTrue(MedicineNameCanonicalizer.isSameMedicine("DOLO 650", "Dolo650"))
        assertTrue(MedicineNameCanonicalizer.isSameMedicine("augmentin", "Augmentin"))
        assertFalse(MedicineNameCanonicalizer.isSameMedicine("Dolo-650", "Augmentin"))
        assertFalse(MedicineNameCanonicalizer.isSameMedicine("Flagyl", "Zyrtec"))
    }
}
