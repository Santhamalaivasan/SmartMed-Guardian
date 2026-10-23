package com.smartmedguardian.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.smartmedguardian.ui.theme.*

data class RiskAssessment(
    val level: RiskLevel,
    val score: Int,
    val reasons: List<String>
)

enum class RiskLevel(val label: String, val color: Color) {
    LOW("LOW RISK", MedicalGreen),
    MEDIUM("MEDIUM RISK", WarningLight),
    HIGH("HIGH RISK", ErrorLight),
    VERY_HIGH("VERY HIGH RISK", ErrorLight)
}

val RiskLevel.themeColor: Color
    @Composable
    get() = when (this) {
        RiskLevel.LOW -> MedicalAccent
        RiskLevel.MEDIUM -> Warning
        RiskLevel.HIGH -> Error
        RiskLevel.VERY_HIGH -> Error
    }

