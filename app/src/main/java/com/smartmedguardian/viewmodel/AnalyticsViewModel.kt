package com.smartmedguardian.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.repository.MedicineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

data class AnalyticsSummary(
    val streakDays: Int = 0,
    val missedCount: Int = 0,
    val lateCount: Int = 0,
    val bestTime: String = "N/A"
)

data class DayMedicineDetail(
    val medicineName: String,
    val dosage: String,
    val timings: List<String>,
    val slotStatuses: Map<String, String>
)

data class TwoDayMedicineData(
    val day1Date: LocalDate,
    val day2Date: LocalDate,
    val day1Label: String,
    val day2Label: String,
    val prescribedCounts: List<Float>,
    val actualCounts: List<Float>,
    val missedCounts: List<Float>,
    val day1Details: List<DayMedicineDetail>,
    val day2Details: List<DayMedicineDetail>,
    val isEmpty: Boolean
)

data class ConditionMedicineDetail(
    val medicineName: String,
    val chemicalComposition: String,
    val dosage: String,
    val prescribedDoses: Int,
    val takenDoses: Int,
    val missedDoses: Int,
    val timings: List<String>,
    val slotStatuses: Map<String, String>
)

data class ConditionAnalysisData(
    val conditionName: String,
    val period: String,
    val labels: List<String>,
    val prescribedCounts: List<Float>,
    val actualCounts: List<Float>,
    val missedCounts: List<Float>,
    val measurementValues1: List<Float>,
    val measurementValues2: List<Float>,
    val measurementValues3: List<Float>,
    val measurementUnit: String,
    val hasMeasurements: Boolean,
    val medicineDetails: List<ConditionMedicineDetail>,
    val totalPrescribed: Int,
    val totalTaken: Int,
    val totalMissed: Int,
    val adherencePercentage: Float,
    val isEmpty: Boolean
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: MedicineRepository
) : ViewModel() {

    private val adherenceRecords: StateFlow<List<AdherenceRecord>> = repository.getAllAdherenceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val twoDayData: StateFlow<TwoDayMedicineData> = combine(
        repository.getAllPrescriptions(),
        repository.getAllAdherenceRecords()
    ) { prescriptions, adherenceRecords ->
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        val days = listOf(yesterday, today)
        val prescribedCounts = mutableListOf<Float>()
        val actualCounts = mutableListOf<Float>()
        val missedCounts = mutableListOf<Float>()
        val day1Details = mutableListOf<DayMedicineDetail>()
        val day2Details = mutableListOf<DayMedicineDetail>()

        days.forEachIndexed { index, date ->
            val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

            val activePrescs = prescriptions.filter { p ->
                val pStart = if (p.startDate > 0) p.startDate else 0L
                val pEnd = if (p.endDate > 0) p.endDate else Long.MAX_VALUE
                startOfDay <= pEnd && endOfDay >= pStart
            }

            var prescribed = 0f
            var actual = 0f
            var missed = 0f

            val detailsList = if (index == 0) day1Details else day2Details

            activePrescs.forEach { presc ->
                val slots = mutableListOf<Pair<String, String>>()
                if (presc.morning) { slots.add("Morning" to presc.morningTime); prescribed += 1f }
                if (presc.afternoon) { slots.add("Afternoon" to presc.afternoonTime); prescribed += 1f }
                if (presc.evening) { slots.add("Evening" to presc.eveningTime); prescribed += 1f }
                if (presc.night) { slots.add("Night" to presc.nightTime); prescribed += 1f }

                val slotStatuses = mutableMapOf<String, String>()
                slots.forEach { (slotName, timeStr) ->
                    val matchingRecord = adherenceRecords.find { rec ->
                        com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(rec.medicineName, presc.medicineName) &&
                        rec.scheduledTime in startOfDay..endOfDay &&
                        (rec.scheduledDoseId?.contains(slotName, ignoreCase = true) == true ||
                         isSlotMatchByTime(rec.scheduledTime, date, timeStr, zoneId))
                    }

                    if (matchingRecord != null) {
                        val st = matchingRecord.status.uppercase()
                        slotStatuses[slotName] = st
                        if (st == "TAKEN" || st == "ON_TIME" || st == "LATE") {
                            actual += 1f
                        } else if (st == "MISSED") {
                            missed += 1f
                        }
                    } else {
                        val currentTime = System.currentTimeMillis()
                        val slotTimestamp = getSlotTimestamp(date, timeStr, zoneId)
                        if (currentTime > slotTimestamp) {
                            slotStatuses[slotName] = "PENDING"
                        } else {
                            slotStatuses[slotName] = "UPCOMING"
                        }
                    }
                }

                detailsList.add(
                    DayMedicineDetail(
                        medicineName = presc.medicineName,
                        dosage = presc.dosage,
                        timings = slots.map { "${it.first} (${it.second})" },
                        slotStatuses = slotStatuses
                    )
                )
            }

            prescribedCounts.add(prescribed)
            actualCounts.add(actual)
            missedCounts.add(missed)
        }

        val isEmpty = prescriptions.isEmpty() && adherenceRecords.isEmpty()

        TwoDayMedicineData(
            day1Date = yesterday,
            day2Date = today,
            day1Label = yesterday.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd", Locale.US)),
            day2Label = today.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd", Locale.US)),
            prescribedCounts = prescribedCounts,
            actualCounts = actualCounts,
            missedCounts = missedCounts,
            day1Details = day1Details,
            day2Details = day2Details,
            isEmpty = isEmpty
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TwoDayMedicineData(
        LocalDate.now().minusDays(1), LocalDate.now(), "Day 1", "Day 2",
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), true
    ))

    private fun isSlotMatchByTime(timestamp: Long, date: LocalDate, timeStr: String, zoneId: ZoneId): Boolean {
        val slotTime = getSlotTimestamp(date, timeStr, zoneId)
        return Math.abs(timestamp - slotTime) < 2 * 3600 * 1000L
    }

    private fun getSlotTimestamp(date: LocalDate, timeStr: String, zoneId: ZoneId): Long {
        val (hour, minute) = try {
            val parts = timeStr.trim().split(":")
            Pair(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            Pair(8, 0)
        }
        return date.atTime(hour, minute).atZone(zoneId).toInstant().toEpochMilli()
    }

    val weeklyAdherence: StateFlow<Map<String, Float>> = repository.getAdherenceSince(getStartOfWeek())
        .map { records ->
            Log.d("AdherenceDebug", "Analytics date range: startOfWeek=${getStartOfWeek()}, recordsLoaded=${records.size}")
            if (records.isEmpty()) {
                emptyMap()
            } else {
                val daysOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val groupedByDay = records.groupBy { getDayName(it.scheduledTime) }

                val result = daysOrder.mapNotNull { day ->
                    val dayRecords = groupedByDay[day]
                    if (dayRecords != null && dayRecords.isNotEmpty()) {
                        val taken = dayRecords.count { 
                            it.status == "ON_TIME" || it.status == "TAKEN" || it.status == "LATE" 
                        }
                        val percentage = (taken.toFloat() / dayRecords.size) * 100f
                        day to percentage
                    } else null
                }.toMap()

                Log.d("AdherenceDebug", "Calculated adherence: $result")
                result
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val summary: StateFlow<AnalyticsSummary> = adherenceRecords.map { records ->
        Log.d("AdherenceDebug", "Adherence records loaded: count=${records.size}")
        val missed = records.count { it.status == "MISSED" }
        val late = records.count { it.status == "LATE" }
        val streak = calculateStreak(records)
        val bestTime = calculateBestTime(records)

        Log.d("AdherenceDebug", "Calculated summary: missed=$missed, late=$late, streak=$streak, bestTime=$bestTime")

        AnalyticsSummary(
            streakDays = streak,
            missedCount = missed,
            lateCount = late,
            bestTime = bestTime
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsSummary())

    private val _selectedCondition = MutableStateFlow("Diabetes")
    private val _selectedPeriod = MutableStateFlow("2 Days")

    val selectedCondition: StateFlow<String> = _selectedCondition
    val selectedPeriod: StateFlow<String> = _selectedPeriod

    fun selectCondition(condition: String) {
        if (_selectedCondition.value != condition) {
            _selectedCondition.value = condition
        }
    }

    fun selectPeriod(period: String) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
        }
    }

    val conditionAnalysis: StateFlow<ConditionAnalysisData> = combine(
        _selectedCondition,
        _selectedPeriod,
        repository.getAllPrescriptions(),
        repository.getAllAdherenceRecords()
    ) { conditionName, period, prescriptions, adherenceRecords ->
        val zoneId = ZoneId.systemDefault()
        val now = LocalDate.now(zoneId)

        val (labels, dayCount) = when (period) {
            "2 Days" -> listOf(
                now.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("MMM dd", Locale.US)),
                now.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd", Locale.US))
            ) to 2
            "Daily" -> listOf("Morning", "Afternoon", "Evening", "Night") to 4
            "Weekly" -> (0..6).map { now.minusDays((6 - it).toLong()).format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.US)) } to 7
            "Monthly" -> (0..3).map { "Week ${it + 1}" } to 4
            else -> listOf("Day 1", "Day 2") to 2
        }

        val conditionMeds = repository.getMedicinesForCondition(conditionName)
        val conditionPrescs = prescriptions.filter { presc ->
            conditionMeds.any { med ->
                com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(presc.medicineName, med)
            }
        }

        val prescribedCounts = MutableList(dayCount) { 0f }
        val actualCounts = MutableList(dayCount) { 0f }
        val missedCounts = MutableList(dayCount) { 0f }
        val medicineDetails = mutableListOf<ConditionMedicineDetail>()

        var totalPresc = 0
        var totalTaken = 0
        var totalMissed = 0

        conditionPrescs.forEach { presc ->
            val medInfo = repository.getMedicineInfo(presc.medicineName)
            val chemicalComp = medInfo?.genericName ?: "Active Compound"

            val slots = mutableListOf<Pair<String, String>>()
            if (presc.morning) slots.add("Morning" to presc.morningTime)
            if (presc.afternoon) slots.add("Afternoon" to presc.afternoonTime)
            if (presc.evening) slots.add("Evening" to presc.eveningTime)
            if (presc.night) slots.add("Night" to presc.nightTime)

            var medPresc = 0
            var medTaken = 0
            var medMissed = 0
            val slotStatuses = mutableMapOf<String, String>()

            when (period) {
                "2 Days" -> {
                    val days = listOf(now.minusDays(1), now)
                    days.forEachIndexed { dIdx, date ->
                        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                        slots.forEach { (_, _) ->
                            medPresc++; totalPresc++
                            prescribedCounts[dIdx] += 1f
                            val rec = adherenceRecords.find { r ->
                                com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(r.medicineName, presc.medicineName) &&
                                r.scheduledTime in startOfDay..endOfDay
                            }
                            if (rec != null) {
                                val st = rec.status.uppercase()
                                if (st == "TAKEN" || st == "ON_TIME" || st == "LATE") {
                                    medTaken++; totalTaken++; actualCounts[dIdx] += 1f
                                } else if (st == "MISSED") {
                                    medMissed++; totalMissed++; missedCounts[dIdx] += 1f
                                }
                            }
                        }
                    }
                }
                "Daily" -> {
                    val startOfDay = now.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = now.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                    slots.forEachIndexed { sIdx, (slotName, _) ->
                        medPresc++; totalPresc++
                        prescribedCounts[sIdx] += 1f
                        val rec = adherenceRecords.find { r ->
                            com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(r.medicineName, presc.medicineName) &&
                            r.scheduledTime in startOfDay..endOfDay &&
                            (r.scheduledDoseId?.contains(slotName, ignoreCase = true) == true)
                        }
                        if (rec != null) {
                            val st = rec.status.uppercase()
                            slotStatuses[slotName] = st
                            if (st == "TAKEN" || st == "ON_TIME" || st == "LATE") {
                                medTaken++; totalTaken++; actualCounts[sIdx] += 1f
                            } else if (st == "MISSED") {
                                medMissed++; totalMissed++; missedCounts[sIdx] += 1f
                            }
                        } else {
                            slotStatuses[slotName] = "PENDING"
                        }
                    }
                }
                "Weekly" -> {
                    for (i in 0..6) {
                        val date = now.minusDays((6 - i).toLong())
                        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                        slots.forEach { _ ->
                            medPresc++; totalPresc++
                            prescribedCounts[i] += 1f
                            val rec = adherenceRecords.find { r ->
                                com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(r.medicineName, presc.medicineName) &&
                                r.scheduledTime in startOfDay..endOfDay
                            }
                            if (rec != null) {
                                val st = rec.status.uppercase()
                                if (st == "TAKEN" || st == "ON_TIME" || st == "LATE") {
                                    medTaken++; totalTaken++; actualCounts[i] += 1f
                                } else if (st == "MISSED") {
                                    medMissed++; totalMissed++; missedCounts[i] += 1f
                                }
                            }
                        }
                    }
                }
                "Monthly" -> {
                    for (w in 0..3) {
                        val weekStart = now.minusDays((3 - w) * 7L)
                        val weekEnd = weekStart.plusDays(7)
                        val startTs = weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
                        val endTs = weekEnd.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                        slots.forEach { _ ->
                            medPresc++; totalPresc++
                            prescribedCounts[w] += 1f
                            val rec = adherenceRecords.find { r ->
                                com.smartmedguardian.util.MedicineNameCanonicalizer.isSameMedicine(r.medicineName, presc.medicineName) &&
                                r.scheduledTime in startTs..endTs
                            }
                            if (rec != null) {
                                val st = rec.status.uppercase()
                                if (st == "TAKEN" || st == "ON_TIME" || st == "LATE") {
                                    medTaken++; totalTaken++; actualCounts[w] += 1f
                                } else if (st == "MISSED") {
                                    medMissed++; totalMissed++; missedCounts[w] += 1f
                                }
                            }
                        }
                    }
                }
            }

            medicineDetails.add(
                ConditionMedicineDetail(
                    medicineName = presc.medicineName,
                    chemicalComposition = chemicalComp,
                    dosage = presc.dosage,
                    prescribedDoses = medPresc,
                    takenDoses = medTaken,
                    missedDoses = medMissed,
                    timings = slots.map { "${it.first} (${it.second})" },
                    slotStatuses = slotStatuses
                )
            )
        }

        val adherencePct = if (totalPresc > 0) (totalTaken.toFloat() / totalPresc) * 100f else 0f
        val isEmpty = conditionPrescs.isEmpty() && adherenceRecords.isEmpty()

        val measUnit = when (conditionName) {
            "Diabetes" -> "mg/dL"
            "Blood Pressure" -> "mmHg"
            "Fever" -> "°C"
            "Thyroid" -> "mIU/L"
            else -> ""
        }

        ConditionAnalysisData(
            conditionName = conditionName,
            period = period,
            labels = labels,
            prescribedCounts = prescribedCounts,
            actualCounts = actualCounts,
            missedCounts = missedCounts,
            measurementValues1 = emptyList(),
            measurementValues2 = emptyList(),
            measurementValues3 = emptyList(),
            measurementUnit = measUnit,
            hasMeasurements = false,
            medicineDetails = medicineDetails,
            totalPrescribed = totalPresc,
            totalTaken = totalTaken,
            totalMissed = totalMissed,
            adherencePercentage = adherencePct,
            isEmpty = isEmpty
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConditionAnalysisData(
        "Diabetes", "2 Days", emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), "mg/dL", false, emptyList(), 0, 0, 0, 0f, true
    ))

    private fun calculateStreak(records: List<AdherenceRecord>): Int {
        if (records.isEmpty()) return 0
        val sortedRecords = records.sortedByDescending { it.scheduledTime }
        var streak = 0
        val zoneId = ZoneId.systemDefault()
        var lastDate: LocalDate? = null

        for (record in sortedRecords) {
            if (record.status == "MISSED") break
            if (record.status == "ON_TIME" || record.status == "TAKEN" || record.status == "LATE") {
                val recordDate = Instant.ofEpochMilli(record.scheduledTime).atZone(zoneId).toLocalDate()
                if (lastDate == null || recordDate != lastDate) {
                    streak++
                    lastDate = recordDate
                }
            }
        }
        return streak
    }

    private fun calculateBestTime(records: List<AdherenceRecord>): String {
        if (records.isEmpty()) return "N/A"

        val takenRecords = records.filter { 
            it.status == "ON_TIME" || it.status == "TAKEN" || it.status == "LATE" 
        }
        if (takenRecords.isEmpty()) return "N/A"

        val zoneId = ZoneId.systemDefault()
        val slotCounts = takenRecords.groupBy { record ->
            val hour = Instant.ofEpochMilli(record.scheduledTime).atZone(zoneId).hour
            when (hour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"
                else -> "Night"
            }
        }.mapValues { it.value.size }

        return slotCounts.maxByOrNull { it.value }?.key ?: "N/A"
    }

    private fun getStartOfWeek(): Long {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun getDayName(timestamp: Long): String {
        val dayOfWeek = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .dayOfWeek
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
        }
    }
}
