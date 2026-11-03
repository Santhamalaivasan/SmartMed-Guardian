package com.smartmedguardian.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.AnalyticsSummary
import com.smartmedguardian.viewmodel.AnalyticsViewModel

@Composable
fun AnalyticsScreen(
    onBackClick: () -> Unit,
    onNavigateToConditionTrend: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val weeklyData by viewModel.weeklyAdherence.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val modelProducer = remember { CartesianChartModelProducer.build() }

    LaunchedEffect(weeklyData) {
        if (weeklyData.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(weeklyData.values.toList())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeumorphicIconButton(
                        onClick = onBackClick,
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Adherence Analytics", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        containerColor = Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            NeumorphicButton(
                text = "Chemical Condition Trend Analysis",
                onClick = onNavigateToConditionTrend,
                modifier = Modifier.fillMaxWidth(),
                color = MedicalGreen,
                textColor = Color.White,
                icon = { Icon(Icons.Default.TrendingUp, null) }
            )

            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text("Weekly Adherence %", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                if (weeklyData.isNotEmpty()) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier.height(200.dp)
                    )
                } else {
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Insufficient data for chart", color = TextLight)
                    }
                }
            }

            TwoDayMedicineGraphSection()

            AdherenceSummaryGrid(summary = summary)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AdherenceSummaryGrid(summary: AnalyticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryItem("Streak", "${summary.streakDays} Days", Icons.Default.Whatshot, MedicalGreen, Modifier.weight(1f))
            SummaryItem("Missed", "${summary.missedCount}", Icons.Default.EventBusy, Error, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryItem("Late", "${summary.lateCount}", Icons.Default.Schedule, Warning, Modifier.weight(1f))
            SummaryItem("Best Time", summary.bestTime, Icons.Default.LightMode, Indigo, Modifier.weight(1f))
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    NeumorphicCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextLight)
        }
    }
}

@Composable
fun TwoDayMedicineGraphSection(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val twoDayData by viewModel.twoDayData.collectAsState()
    val modelProducer = remember { CartesianChartModelProducer.build() }

    LaunchedEffect(twoDayData) {
        if (!twoDayData.isEmpty && twoDayData.prescribedCounts.size == 2) {
            modelProducer.runTransaction {
                columnSeries {
                    series(twoDayData.prescribedCounts)
                    series(twoDayData.actualCounts)
                    series(twoDayData.missedCounts)
                }
            }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1E1E2C)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "2-Day Medicine History & Graph",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${twoDayData.day1Label} vs ${twoDayData.day2Label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )
                }
                Surface(
                    color = MedicalGreen.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "2-Day Comparison",
                        color = MedicalGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                LegendItem("Prescribed", Indigo)
                LegendItem("Actual (Taken)", MedicalGreen)
                LegendItem("Missed", Error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!twoDayData.isEmpty && twoDayData.prescribedCounts.isNotEmpty()) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(),
                        startAxis = rememberStartAxis(
                            label = com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent(color = Color.White)
                        ),
                        bottomAxis = rememberBottomAxis(
                            label = com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent(color = Color.White)
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.height(200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Daily Details Summary
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Daily Details Summary",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                DaySummaryColumn(title = twoDayData.day1Label, details = twoDayData.day1Details)
                Spacer(modifier = Modifier.height(12.dp))
                DaySummaryColumn(title = twoDayData.day2Label, details = twoDayData.day2Details)

            } else {
                Box(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No adherence records or prescriptions found for the past 2 days.",
                        color = TextLight,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, MaterialTheme.shapes.extraSmall)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
fun DaySummaryColumn(title: String, details: List<com.smartmedguardian.viewmodel.DayMedicineDetail>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MedicalAccent
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (details.isEmpty()) {
            Text(text = "No medicines scheduled for this day.", style = MaterialTheme.typography.bodySmall, color = TextLight)
        } else {
            details.forEach { detail ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = detail.medicineName,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (detail.dosage.isNotBlank()) {
                                Text(
                                    text = detail.dosage,
                                    color = MedicalGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            detail.slotStatuses.forEach { (slot, status) ->
                                val statusColor = when (status) {
                                    "TAKEN", "ON_TIME", "LATE" -> MedicalGreen
                                    "MISSED" -> Error
                                    else -> TextLight
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "$slot: $status",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
