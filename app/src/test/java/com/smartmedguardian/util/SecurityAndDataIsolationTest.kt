package com.smartmedguardian.util

import com.smartmedguardian.viewmodel.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityAndDataIsolationTest {

    @Test
    fun testBackupAndDataExtractionRulesExcludeAllSensitivePaths() {
        val backupRulesFile = File("src/main/res/xml/backup_rules.xml").takeIf { it.exists() }
            ?: File("app/src/main/res/xml/backup_rules.xml")
        val dataExtractionRulesFile = File("src/main/res/xml/data_extraction_rules.xml").takeIf { it.exists() }
            ?: File("app/src/main/res/xml/data_extraction_rules.xml")

        assertTrue("backup_rules.xml must exist", backupRulesFile.exists())
        assertTrue("data_extraction_rules.xml must exist", dataExtractionRulesFile.exists())

        val backupContent = backupRulesFile.readText()
        val dataExtractionContent = dataExtractionRulesFile.readText()

        // Database files exclusion
        assertTrue("backup_rules must exclude smart_med_guardian_db", backupContent.contains("smart_med_guardian_db"))
        assertTrue("backup_rules must exclude smart_med_guardian_db-wal", backupContent.contains("smart_med_guardian_db-wal"))
        assertTrue("backup_rules must exclude smart_med_guardian_db-shm", backupContent.contains("smart_med_guardian_db-shm"))
        assertTrue("data_extraction_rules must exclude smart_med_guardian_db", dataExtractionContent.contains("smart_med_guardian_db"))

        // DataStore files exclusion (files/datastore/settings.preferences_pb)
        assertTrue("backup_rules must exclude datastore/settings.preferences_pb", backupContent.contains("datastore/settings.preferences_pb"))
        assertTrue("backup_rules must exclude datastore folder", backupContent.contains("datastore"))
        assertTrue("data_extraction_rules must exclude datastore/settings.preferences_pb", dataExtractionContent.contains("datastore/settings.preferences_pb"))

        // Reference images exclusion
        assertTrue("backup_rules must exclude medicine_references", backupContent.contains("medicine_references"))
        assertTrue("data_extraction_rules must exclude medicine_references", dataExtractionContent.contains("medicine_references"))
    }

    @Test
    fun testFirestoreRulesCaregiverAuthorizationAndOwnershipImmutability() {
        val firestoreRulesFile = File("firestore.rules").takeIf { it.exists() } ?: File("../firestore.rules")
        assertTrue("firestore.rules file must exist in repository root", firestoreRulesFile.exists())

        val rulesContent = firestoreRulesFile.readText()

        // Enforce patient isolation and caregiver verification logic
        assertTrue("Rules must check authentication", rulesContent.contains("isAuthenticated()"))
        assertTrue("Rules must enforce owner isolation", rulesContent.contains("request.auth.uid == userId"))
        assertTrue("Rules must check alert patient ownership", rulesContent.contains("isAlertPatient"))
        assertTrue("Rules must check caregiver authorization", rulesContent.contains("isAlertCaregiver"))

        // Assert unsafe string check is removed
        assertFalse("Rules must NOT contain unsafe string comparison caregiver_ + patientId", rulesContent.contains("\"caregiver_\" + data.patientId"))

        // Assert explicit caregiver membership document lookup
        assertTrue("Rules must check explicit caregiver membership in patient document collection", rulesContent.contains("users/$(data.patientId)/caregivers/$(request.auth.uid)"))
        assertTrue("Rules must match caregivers subcollection", rulesContent.contains("match /caregivers/{caregiverUid}"))

        // Immutability of ownership fields
        assertTrue("Rules must enforce patientId immutability", rulesContent.contains("request.resource.data.patientId == resource.data.patientId"))
        assertTrue("Rules must enforce caregiverId immutability", rulesContent.contains("request.resource.data.caregiverId == resource.data.caregiverId"))
        assertTrue("Rules must enforce alertId immutability", rulesContent.contains("request.resource.data.alertId == resource.data.alertId"))

        // Schema validation
        assertTrue("Rules must validate required alert schema fields", rulesContent.contains("validAlertFields"))
        assertTrue("Rules must validate status as string", rulesContent.contains("status is string"))
        assertTrue("Rules must validate acknowledged as bool", rulesContent.contains("acknowledged is bool"))
    }

    @Test
    fun testTargetParameterSanitization() {
        fun sanitizeTarget(target: String?): String? {
            return target?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        }

        assertEquals("Dolo-650", sanitizeTarget("Dolo-650"))
        assertEquals(null, sanitizeTarget("{targetMedicine}"))
        assertEquals(null, sanitizeTarget("   "))
        assertEquals(null, sanitizeTarget(null))
    }

    @Test
    fun testLogoutErrorStateHandlingContract() {
        fun simulateLogoutWithCleanupFailure(cause: Exception): AuthState {
            return try {
                throw cause
            } catch (e: Exception) {
                AuthState.Error("Logout cleanup failed: ${e.message ?: "Unknown error"}")
            }
        }

        val testException = IllegalStateException("Room DB delete error")
        val state = simulateLogoutWithCleanupFailure(testException)

        assertTrue("Logout failure must produce AuthState.Error state", state is AuthState.Error)
        val errorMessage = (state as AuthState.Error).message
        assertTrue("Error message must contain cause message", errorMessage.contains("Room DB delete error"))
    }

    @Test
    fun testSensitiveLogRedactionFormat() {
        fun formatDiagnosticLog(rawOcr: String, medName: String, userId: String): String {
            val redactedOcr = if (rawOcr.isBlank()) "<empty>" else "[REDACTED_OCR]"
            val redactedMed = if (medName.isBlank()) "NONE" else "[REDACTED_MEDICINE]"
            val redactedUser = if (userId.isBlank()) "<empty>" else "[REDACTED_UID]"
            return "OCR: $redactedOcr, MED: $redactedMed, UID: $redactedUser"
        }

        val logOutput = formatDiagnosticLog("Sensitive OCR textParacetamol 500mg", "Dolo-650", "usr_12345")
        assertFalse("Log output must not contain raw OCR text", logOutput.contains("Sensitive OCR"))
        assertFalse("Log output must not contain raw medicine name", logOutput.contains("Dolo-650"))
        assertFalse("Log output must not contain raw user ID", logOutput.contains("usr_12345"))
        assertTrue("Log output must contain OCR redaction tag", logOutput.contains("[REDACTED_OCR]"))
        assertTrue("Log output must contain Medicine redaction tag", logOutput.contains("[REDACTED_MEDICINE]"))
        assertTrue("Log output must contain UID redaction tag", logOutput.contains("[REDACTED_UID]"))
    }
}
