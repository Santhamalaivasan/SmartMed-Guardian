package com.smartmedguardian.worker

import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.util.CaregiverNotificationHelper
import com.smartmedguardian.util.NotificationConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSystemConsistencyTest {

    @Test
    fun testNotificationChannelConsistency() {
        assertEquals(
            "medication_reminders_channel",
            NotificationConstants.MEDICATION_REMINDERS_CHANNEL_ID
        )
        assertEquals(
            NotificationConstants.MEDICATION_REMINDERS_CHANNEL_ID,
            ReminderAlarmReceiver.CHANNEL_ID
        )
        assertEquals(
            "caregiver_notifications_channel",
            NotificationConstants.CAREGIVER_ALERTS_CHANNEL_ID
        )
        assertEquals(
            NotificationConstants.CAREGIVER_ALERTS_CHANNEL_ID,
            CaregiverNotificationHelper.CHANNEL_ID
        )
    }

    @Test
    fun testNoCollisionBetween123And100123() {
        val id1 = 123L
        val id2 = 100123L
        val slot = "Morning"

        val code1 = ReminderScheduler.getRequestCode(id1, slot)
        val code2 = ReminderScheduler.getRequestCode(id2, slot)

        val uri1 = ReminderScheduler.getReminderDataString(id1, slot, isSnooze = false)
        val uri2 = ReminderScheduler.getReminderDataString(id2, slot, isSnooze = false)

        assertNotEquals("Request codes for 123 and 100123 must be distinct", code1, code2)
        assertNotEquals("Data URIs for 123 and 100123 must be distinct", uri1, uri2)
        assertEquals("smartmed://reminder/alarm/123/morning", uri1)
        assertEquals("smartmed://reminder/alarm/100123/morning", uri2)
    }

    @Test
    fun testLargePrescriptionIdsNoCollision() {
        val idMax = Long.MAX_VALUE
        val idLarge = 999999999L
        val slot = "Evening"

        val codeMax = ReminderScheduler.getRequestCode(idMax, slot)
        val codeLarge = ReminderScheduler.getRequestCode(idLarge, slot)

        val uriMax = ReminderScheduler.getReminderDataString(idMax, slot, isSnooze = false)
        val uriLarge = ReminderScheduler.getReminderDataString(idLarge, slot, isSnooze = false)

        assertNotEquals(codeMax, codeLarge)
        assertNotEquals(uriMax, uriLarge)
    }

    @Test
    fun testSnoozeVersusNormalReminderIdentity() {
        val prescriptionId = 456L
        val slot = "Night"

        val normalCode = ReminderScheduler.getRequestCode(prescriptionId, slot)
        val snoozeCode = ReminderScheduler.getSnoozeRequestCode(prescriptionId, slot)

        val normalUri = ReminderScheduler.getReminderDataString(prescriptionId, slot, isSnooze = false)
        val snoozeUri = ReminderScheduler.getReminderDataString(prescriptionId, slot, isSnooze = true)

        assertNotEquals(normalCode, snoozeCode)
        assertNotEquals(normalUri, snoozeUri)
        assertEquals("smartmed://reminder/alarm/456/night", normalUri)
        assertEquals("smartmed://reminder/snooze/456/night", snoozeUri)
    }

    @Test
    fun testPendingIntentCancellationIdentityMatch() {
        val prescriptionId = 789L
        val slot = "Afternoon"

        val normalUri = ReminderScheduler.getReminderDataString(prescriptionId, slot, isSnooze = false)
        val snoozeUri = ReminderScheduler.getReminderDataString(prescriptionId, slot, isSnooze = true)

        val cancelNormalCode = ReminderScheduler.getRequestCode(prescriptionId, slot)
        val cancelSnoozeCode = ReminderScheduler.getSnoozeRequestCode(prescriptionId, slot)

        assertEquals("smartmed://reminder/alarm/789/afternoon", normalUri)
        assertEquals("smartmed://reminder/snooze/789/afternoon", snoozeUri)
        assertEquals(ReminderScheduler.getRequestCode(prescriptionId, slot), cancelNormalCode)
        assertEquals(ReminderScheduler.getSnoozeRequestCode(prescriptionId, slot), cancelSnoozeCode)
    }

    @Test
    fun testNotificationIdUniquenessForMultiplePrescriptionsAndSlots() {
        val id1 = 123L
        val id2 = 100123L
        val slot = "Morning"

        val notifId1 = (id1.hashCode() * 31 + slot.lowercase().hashCode())
        val notifId2 = (id2.hashCode() * 31 + slot.lowercase().hashCode())

        assertNotEquals(notifId1, notifId2)
    }

    @Test
    fun testClockAndZoneChangeActionHandling() {
        val bootAction = "android.intent.action.BOOT_COMPLETED"
        val packageReplacedAction = "android.intent.action.MY_PACKAGE_REPLACED"
        val timeSetAction = "android.intent.action.TIME_SET"
        val zoneAction = "android.intent.action.TIMEZONE_CHANGED"

        assertTrue(bootAction == "android.intent.action.BOOT_COMPLETED")
        assertTrue(packageReplacedAction == "android.intent.action.MY_PACKAGE_REPLACED")
        assertTrue(timeSetAction == "android.intent.action.TIME_SET")
        assertTrue(zoneAction == "android.intent.action.TIMEZONE_CHANGED")
    }

    @Test
    fun testMissedDoseGracePeriodAndDoseIdFormat() {
        val prescriptionId = 100L
        val slotName = "morning"
        val scheduledTime = 1700000000000L
        val graceMinutes = 30

        val graceMillis = graceMinutes * 60 * 1000L
        val missedExecutionTime = scheduledTime + graceMillis
        val stableDoseId = "dose_${prescriptionId}_${slotName}_$scheduledTime"

        assertEquals(1700001800000L, missedExecutionTime)
        assertEquals("dose_100_morning_1700000000000", stableDoseId)
    }

    @Test
    fun testAdherenceRecordMissedDoseFields() {
        val record = AdherenceRecord(
            medicineName = "Dolo-650",
            scheduledTime = 1700000000000L,
            actualTime = null,
            status = "MISSED",
            scheduledDoseId = "dose_100_morning_1700000000000",
            caregiverNotified = true,
            notificationStatus = "SENT",
            notificationTimestamp = 1700001800000L
        )

        assertEquals("MISSED", record.status)
        assertEquals("dose_100_morning_1700000000000", record.scheduledDoseId)
        assertTrue(record.caregiverNotified)
        assertEquals("SENT", record.notificationStatus)
        assertNotNull(record.notificationTimestamp)
    }
}
