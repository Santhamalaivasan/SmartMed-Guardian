package com.smartmedguardian.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.repository.MedicineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class AnalyticsSummary(
    val streakDays: Int = 0,
    val missedCount: Int = 0,
    val lateCount: Int = 0,
    val bestTime: String = "N/A"
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: MedicineRepository
) : ViewModel() {

    private val adherenceRecords: StateFlow<List<AdherenceRecord>> = repository.getAllAdherenceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        Log.d("AdherenceDebug", "Adherence records loaded: count=${records.size}, records=$records")
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
