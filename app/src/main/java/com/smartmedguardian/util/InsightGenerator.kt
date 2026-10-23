package com.smartmedguardian.util

import com.smartmedguardian.data.entity.AdherenceRecord
import java.util.*

class InsightGenerator {

    fun generateInsights(records: List<AdherenceRecord>): List<String> {
        val insights = mutableListOf<String>()
        if (records.size < 5) return listOf("Keep taking your medicines to see personal insights.")

        // 1. Time of day pattern
        val missedByTime = records.filter { it.status == "MISSED" }
            .groupBy { getPartOfDay(it.scheduledTime) }
        
        val worstTime = missedByTime.maxByOrNull { it.value.size }
        if (worstTime != null && worstTime.value.size > 2) {
            insights.add("You usually miss your ${worstTime.key.lowercase()} doses.")
        }

        // 2. Improvement insight
        val recentAdherence = calculateAdherence(records.take(10))
        val previousAdherence = calculateAdherence(records.drop(10).take(10))
        
        if (previousAdherence > 0) {
            val diff = recentAdherence - previousAdherence
            if (diff > 10) {
                insights.add("You improved your adherence by ${diff.toInt()}% recently!")
            } else if (diff < -15) {
                insights.add("Your adherence has dropped by ${(-diff).toInt()}% lately.")
            }
        }

        // 3. Late pattern
        val lateCount = records.count { it.status == "LATE" }
        if (lateCount > records.size * 0.3) {
            insights.add("You frequently delay your medication. Try to be more punctual.")
        }

        if (insights.isEmpty()) {
            insights.add("Your adherence pattern is stable. Good job!")
        }

        return insights
    }

    private fun getPartOfDay(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
    }

    private fun calculateAdherence(records: List<AdherenceRecord>): Float {
        if (records.isEmpty()) return 0f
        val taken = records.count { it.status == "ON_TIME" || it.status == "TAKEN" || it.status == "LATE" }
        return (taken.toFloat() / records.size) * 100
    }
}
