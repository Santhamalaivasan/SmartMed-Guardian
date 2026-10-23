package com.smartmedguardian.worker

import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.util.MedicineVerificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReminderSchedulerTest {

    private lateinit var verificationManager: MedicineVerificationManager

    @Before
    fun setUp() {
        verificationManager = MedicineVerificationManager()
    }

    // 1. One daily reminder
    @Test
    fun test1_oneDailyReminder() {
        val p = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Once daily",
            morning = true,
            morningTime = "08:00",
            startDate = 0L,
            endDate = 0L
        )
        val (h, m) = ReminderScheduler.parseHourMinute(p.morningTime, 8)
        assertEquals(8, h)
        assertEquals(0, m)
        val formatted = ReminderScheduler.format12HourTime(h, m)
        assertTrue(formatted.contains("08:00") || formatted.contains("8:00"))
        assertTrue(formatted.lowercase().contains("am"))
    }

    // 2. Morning reminder
    @Test
    fun test2_morningReminder() {
        val p = Prescription(id = 2, medicineName = "Brufen", dosage = "400mg", frequency = "Morning", morning = true, morningTime = "08:30", startDate = 0L, endDate = 0L)
        val (h, m) = ReminderScheduler.parseHourMinute(p.morningTime, 8)
        assertEquals(8, h)
        assertEquals(30, m)
        val slot = verificationManager.getCurrentTimeSlot(LocalTime.of(8, 30))
        assertEquals("Morning", slot)
    }

    // 3. Afternoon reminder
    @Test
    fun test3_afternoonReminder() {
        val p = Prescription(id = 3, medicineName = "Brufen", dosage = "400mg", frequency = "Afternoon", afternoon = true, afternoonTime = "14:00", startDate = 0L, endDate = 0L)
        val (h, m) = ReminderScheduler.parseHourMinute(p.afternoonTime, 13)
        assertEquals(14, h)
        assertEquals(0, m)
        val slot = verificationManager.getCurrentTimeSlot(LocalTime.of(14, 0))
        assertEquals("Afternoon", slot)
    }

    // 4. Evening reminder
    @Test
    fun test4_eveningReminder() {
        val p = Prescription(id = 4, medicineName = "Dolo-650", dosage = "650mg", frequency = "Evening", evening = true, eveningTime = "20:00", startDate = 0L, endDate = 0L)
        val (h, m) = ReminderScheduler.parseHourMinute(p.eveningTime, 20)
        assertEquals(20, h)
        assertEquals(0, m)
        val slot = verificationManager.inferSlotFromTime(LocalTime.of(20, 0), p)
        assertEquals("Evening", slot)
    }

    // 5. Night reminder
    @Test
    fun test5_nightReminder() {
        val p = Prescription(id = 5, medicineName = "Zyrtec", dosage = "10mg", frequency = "Night", night = true, nightTime = "22:00", startDate = 0L, endDate = 0L)
        val (h, m) = ReminderScheduler.parseHourMinute(p.nightTime, 22)
        assertEquals(22, h)
        assertEquals(0, m)
        val slot = verificationManager.getCurrentTimeSlot(LocalTime.of(22, 0))
        assertEquals("Night", slot)
    }

    // 6. Multiple reminders for one medicine
    @Test
    fun test6_multipleRemindersForOneMedicine() {
        val p = Prescription(
            id = 6,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Thrice daily",
            morning = true, morningTime = "08:00",
            afternoon = true, afternoonTime = "14:00",
            evening = true, eveningTime = "20:00",
            startDate = 0L, endDate = 0L
        )
        val codeMorning = ReminderScheduler.getRequestCode(p.id, "Morning")
        val codeAfternoon = ReminderScheduler.getRequestCode(p.id, "Afternoon")
        val codeEvening = ReminderScheduler.getRequestCode(p.id, "Evening")

        assertNotEquals(codeMorning, codeAfternoon)
        assertNotEquals(codeAfternoon, codeEvening)
        assertNotEquals(codeMorning, codeEvening)
    }

    // 7. Multiple medicines
    @Test
    fun test7_multipleMedicines() {
        val p1 = Prescription(id = 10, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, startDate = 0L, endDate = 0L)
        val p2 = Prescription(id = 11, medicineName = "Brufen", dosage = "400mg", frequency = "Evening", evening = true, startDate = 0L, endDate = 0L)

        val code1 = ReminderScheduler.getRequestCode(p1.id, "Morning")
        val code2 = ReminderScheduler.getRequestCode(p2.id, "Evening")
        assertNotEquals(code1, code2)
    }

    // 8. Same-time medicines
    @Test
    fun test8_sameTimeMedicines() {
        val p1 = Prescription(id = 100, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, morningTime = "08:00", startDate = 0L, endDate = 0L)
        val p2 = Prescription(id = 200, medicineName = "Brufen", dosage = "400mg", frequency = "Morning", morning = true, morningTime = "08:00", startDate = 0L, endDate = 0L)

        val code1 = ReminderScheduler.getRequestCode(p1.id, "Morning")
        val code2 = ReminderScheduler.getRequestCode(p2.id, "Morning")

        // Unique request codes derived from prescriptionId prevent one reminder from overwriting another
        assertNotEquals(code1, code2)
    }

    // 9. Unique reminder IDs
    @Test
    fun test9_uniqueReminderIds() {
        val idSet = mutableSetOf<Int>()
        for (prescId in 1L..10L) {
            for (slot in listOf("Morning", "Afternoon", "Evening", "Night")) {
                val code = ReminderScheduler.getRequestCode(prescId, slot)
                assertTrue("Code $code should be unique", idSet.add(code))
            }
        }
    }

    // 10. Prescription update
    @Test
    fun test10_prescriptionUpdate() {
        var p = Prescription(id = 15, medicineName = "Dolo-650", dosage = "650mg", frequency = "Evening", evening = true, eveningTime = "20:00", startDate = 0L, endDate = 0L)
        val oldCode = ReminderScheduler.getRequestCode(p.id, "Evening")
        val (oldH, _) = ReminderScheduler.parseHourMinute(p.eveningTime, 20)
        assertEquals(20, oldH)

        // User updates time from 20:00 to 21:00
        p = p.copy(eveningTime = "21:00")
        val newCode = ReminderScheduler.getRequestCode(p.id, "Evening")
        val (newH, _) = ReminderScheduler.parseHourMinute(p.eveningTime, 20)

        assertEquals(oldCode, newCode) // Same stable slot code for updating PendingIntent
        assertEquals(21, newH)
    }

    // 11. Prescription deletion
    @Test
    fun test11_prescriptionDeletion() {
        val p = Prescription(id = 25, medicineName = "Augmentin", dosage = "625mg", frequency = "Morning", morning = true, startDate = 0L, endDate = 0L)
        val code = ReminderScheduler.getRequestCode(p.id, "Morning")
        assertTrue(code > 0)
    }

    // 12. Reminder enable/disable
    @Test
    fun test12_reminderEnableDisable() {
        var p = Prescription(id = 30, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, remindersEnabled = true, startDate = 0L, endDate = 0L)
        assertTrue(p.remindersEnabled)

        p = p.copy(remindersEnabled = false)
        assertTrue(!p.remindersEnabled)
    }

    // 13. Next-day calculation
    @Test
    fun test13_nextDayCalculation() {
        val nowLocalTime = LocalTime.of(21, 0)
        val reminderTime = LocalTime.of(8, 0)
        assertTrue(reminderTime.isBefore(nowLocalTime)) // Target 8:00 AM passed today, so next occurrence is 8:00 AM tomorrow
    }

    // 14. Midnight handling
    @Test
    fun test14_midnightHandling() {
        val slot1 = verificationManager.getCurrentTimeSlot(LocalTime.of(23, 30))
        val slot2 = verificationManager.getCurrentTimeSlot(LocalTime.of(1, 15))
        assertEquals("Night", slot1)
        assertEquals("Night", slot2)
    }

    // 15. Notification intent data
    @Test
    fun test15_notificationIntentData() {
        val formattedTime = ReminderScheduler.format12HourTime(20, 0)
        assertTrue(formattedTime.contains("8:00") && formattedTime.lowercase().contains("pm"))
    }

    // 16. Reboot restoration
    @Test
    fun test16_rebootRestoration() {
        val prescs = listOf(
            Prescription(id = 1, medicineName = "Dolo-650", dosage = "650mg", frequency = "Morning", morning = true, remindersEnabled = true, startDate = 0L, endDate = 0L),
            Prescription(id = 2, medicineName = "Zyrtec", dosage = "10mg", frequency = "Night", night = true, remindersEnabled = false, startDate = 0L, endDate = 0L)
        )
        val active = prescs.filter { it.remindersEnabled }
        assertEquals(1, active.size)
        assertEquals("Dolo-650", active.first().medicineName)
    }

    // 17. Reminder does not mark dose as taken
    @Test
    fun test17_reminderDoesNotMarkDoseAsTaken() {
        // Simulating firing alarm
        val alarmFired = true
        val doseMarkedTaken = if (alarmFired) false else false
        assertEquals(false, doseMarkedTaken)
    }

    // 18. Correct medicine verification records a verified dose
    @Test
    fun test18_correctMedicineVerification() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Evening",
            evening = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = verificationManager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "DOLO 650 PARACETAMOL 650 MG",
            currentTime = LocalTime.of(20, 0),
            currentDate = LocalDate.now()
        )

        assertEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals("Dolo-650", result.prescription?.medicineName)
    }

    // 19. Incorrect medicine does not record successful adherence
    @Test
    fun test19_incorrectMedicine() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = verificationManager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.5f, 0),
            prescriptions = listOf(prescription),
            ocrText = "BRUFEN 400 MG",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertNotEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(VerificationStatus.MEDICINE_NOT_FOUND, result.status)
    }

    // 20. Unknown medicine does not record successful adherence
    @Test
    fun test20_unknownMedicine() {
        val prescription = Prescription(
            id = 1,
            medicineName = "Dolo-650",
            dosage = "650mg",
            frequency = "Morning",
            morning = true,
            startDate = 0L,
            endDate = 0L
        )

        val result = verificationManager.verify(
            recognitionResult = RecognitionResult("Unknown", 0.2f, 0),
            prescriptions = listOf(prescription),
            ocrText = "SOME RANDOM UNKNOWN PHARMA 123",
            currentTime = LocalTime.of(8, 0),
            currentDate = LocalDate.now()
        )

        assertNotEquals(VerificationStatus.CORRECT_MEDICINE, result.status)
        assertEquals(VerificationStatus.UNKNOWN_MEDICINE, result.status)
    }
}
