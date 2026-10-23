package com.smartmedguardian.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.model.RiskAssessment
import com.smartmedguardian.model.RiskLevel
import com.smartmedguardian.model.themeColor
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.util.AdherenceTrend
import com.smartmedguardian.viewmodel.DashboardViewModel
import com.smartmedguardian.worker.ReminderScheduler

@Composable
fun DashboardScreen(
    onScanClick: () -> Unit,
    onPrescriptionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onCaregiverClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEvaluationClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val risk by viewModel.riskAssessment.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val prescriptions by viewModel.prescriptions.collectAsState()
    
    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Good Morning,", style = MaterialTheme.typography.titleMedium, color = TextLight)
                        Text("User Name", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    NeumorphicIconButton(
                        onClick = onSettingsClick,
                        icon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onBackground) }
                    )
                }
            }
        },
        containerColor = Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            RiskCard(risk)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardActionCard(
                    title = "Prescriptions",
                    icon = Icons.Default.MedicalServices,
                    modifier = Modifier.weight(1f),
                    onClick = onPrescriptionClick
                )
                DashboardActionCard(
                    title = "History",
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f),
                    onClick = onHistoryClick
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardActionCard(
                    title = "Analytics",
                    icon = Icons.Default.BarChart,
                    modifier = Modifier.weight(1f),
                    onClick = onAnalyticsClick
                )
                DashboardActionCard(
                    title = "Caregiver",
                    icon = Icons.Default.FamilyRestroom,
                    modifier = Modifier.weight(1f),
                    onClick = onCaregiverClick
                )
            }

            NeumorphicButton(
                text = "Evaluation Mode",
                onClick = onEvaluationClick,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Default.Assessment, null) }
            )

            InsightSection(insights)

            NeumorphicButton(
                text = "Scan Medicine",
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                icon = { Icon(Icons.Default.CameraAlt, null) }
            )

            TodayMedicineSection(prescriptions)
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PredictionStatsCard(missedProb: Float, trend: com.smartmedguardian.util.AdherenceTrend) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Missed Dose Probability", style = MaterialTheme.typography.labelSmall)
                Text("${(missedProb * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (missedProb > 0.5) Error else MedicalAccent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Trend", style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color) = when(trend) {
                        AdherenceTrend.IMPROVING -> Icons.Default.TrendingUp to MedicalAccent
                        AdherenceTrend.DECLINING -> Icons.Default.TrendingDown to Error
                        AdherenceTrend.STABLE -> Icons.Default.TrendingFlat to MaterialTheme.colorScheme.secondary
                    }
                    Icon(icon, null, tint = color)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(trend.name, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RiskCard(risk: RiskAssessment) {
    var expanded by remember { mutableStateOf(false) }
    val riskColor = risk.level.themeColor
    
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Adherence Risk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(risk.level.label, color = riskColor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(riskColor.copy(alpha = 0.15f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = riskColor)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { risk.score / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = riskColor,
                trackColor = riskColor.copy(alpha = 0.15f)
            )
            
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide Analysis" else "View Why", color = MaterialTheme.colorScheme.primary)
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    risk.reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Circle, null, modifier = Modifier.size(6.dp), tint = riskColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(reason, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardActionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    NeumorphicCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MedicalAccent, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun InsightSection(insights: List<String>) {
    Column {
        Text("Personal Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        insights.forEach { insight ->
            NeumorphicCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TipsAndUpdates, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(insight, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun TodayMedicineSection(prescriptions: List<com.smartmedguardian.data.entity.Prescription>) {
    Column {
        Text("Today's Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        if (prescriptions.isEmpty()) {
            Text("No medicines scheduled.", color = TextLight)
        } else {
            prescriptions.take(5).forEach { presc ->
                NeumorphicCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(presc.medicineName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (presc.dosage.isNotBlank()) {
                                Text(presc.dosage, style = MaterialTheme.typography.bodySmall)
                            }
                            
                            val slots = mutableListOf<String>()
                            if (presc.morning) {
                                val (h, m) = ReminderScheduler.parseHourMinute(presc.morningTime, 8)
                                slots.add("Morn ${ReminderScheduler.format12HourTime(h, m)}")
                            }
                            if (presc.afternoon) {
                                val (h, m) = ReminderScheduler.parseHourMinute(presc.afternoonTime, 13)
                                slots.add("Aft ${ReminderScheduler.format12HourTime(h, m)}")
                            }
                            if (presc.evening) {
                                val (h, m) = ReminderScheduler.parseHourMinute(presc.eveningTime, 20)
                                slots.add("Eve ${ReminderScheduler.format12HourTime(h, m)}")
                            }
                            if (presc.night) {
                                val (h, m) = ReminderScheduler.parseHourMinute(presc.nightTime, 22)
                                slots.add("Night ${ReminderScheduler.format12HourTime(h, m)}")
                            }
                            
                            if (slots.isNotEmpty()) {
                                Text(
                                    text = slots.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MedicalAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextLight)
                    }
                }
            }
        }
    }
}
