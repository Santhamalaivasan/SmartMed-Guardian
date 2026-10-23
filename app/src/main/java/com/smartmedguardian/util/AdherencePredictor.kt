package com.smartmedguardian.util

import com.smartmedguardian.data.entity.AdherenceRecord

class AdherencePredictor {

    fun predictMissedDoseProbability(records: List<AdherenceRecord>): Float {
        if (records.size < 5) return 0.1f
        
        // Simple heuristic: probability based on recent misses
        val recentRecords = records.take(10)
        val missedCount = recentRecords.count { it.status == "MISSED" }
        
        var probability = missedCount.toFloat() / recentRecords.size
        
        // Adjust for patterns (e.g., if last 2 were missed, increase probability)
        if (records.take(2).all { it.status == "MISSED" }) {
            probability += 0.2f
        }
        
        return minOf(probability, 1.0f)
    }

    fun getAdherenceTrend(records: List<AdherenceRecord>): AdherenceTrend {
        if (records.size < 10) return AdherenceTrend.STABLE
        
        val recent = calculateAdherence(records.take(5))
        val previous = calculateAdherence(records.drop(5).take(5))
        
        return when {
            recent > previous + 10 -> AdherenceTrend.IMPROVING
            recent < previous - 10 -> AdherenceTrend.DECLINING
            else -> AdherenceTrend.STABLE
        }
    }

    private fun calculateAdherence(records: List<AdherenceRecord>): Float {
        if (records.isEmpty()) return 0f
        return (records.count { it.status == "ON_TIME" || it.status == "TAKEN" || it.status == "LATE" }.toFloat() / records.size) * 100
    }
}

enum class AdherenceTrend {
    IMPROVING, DECLINING, STABLE
}
