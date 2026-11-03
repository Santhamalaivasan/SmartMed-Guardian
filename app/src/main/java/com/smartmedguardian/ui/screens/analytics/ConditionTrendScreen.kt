package com.smartmedguardian.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import com.smartmedguardian.viewmodel.AnalyticsViewModel
import com.smartmedguardian.viewmodel.ConditionAnalysisData
import com.smartmedguardian.viewmodel.ConditionMedicineDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionTrendScreen(
    onBackClick: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val selectedCondition by viewModel.selectedCondition.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val currentData by viewModel.conditionAnalysis.collectAsState()

    val modelProducer = remember { CartesianChartModelProducer.build() }

    LaunchedEffect(currentData) {
        if (!currentData.isEmpty && currentData.prescribedCounts.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(currentData.prescribedCounts)
                    series(currentData.actualCounts)
                    series(currentData.missedCounts)
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
                    Text("Health Trend Analysis", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        containerColor = Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Condition Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Diabetes", "Blood Pressure", "Fever", "Thyroid").forEach { cond ->
                    FilterChip(
                        selected = selectedCondition == cond,
                        onClick = { viewModel.selectCondition(cond) },
                        label = { Text(cond, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Period Selector Tabs (2 Days, Daily, Weekly, Monthly)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("2 Days", "Daily", "Weekly", "Monthly").forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Dark Theme Medication Doses Graph Card
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E2C)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$selectedCondition ($selectedPeriod) - Doses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Prescribed vs Actual vs Missed",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLight
                            )
                        }
                        Surface(
                            color = MedicalGreen.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${currentData.adherencePercentage.toInt()}% Adherence",
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

                    if (!currentData.isEmpty && currentData.prescribedCounts.isNotEmpty()) {
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

                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Medication Dosage & Intake Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        currentData.medicineDetails.forEach { detail ->
                            MedicineDetailCard(detail = detail)
                        }

                    } else {
                        Box(
                            modifier = Modifier
                                .height(200.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No adherence records or prescriptions found for $selectedCondition ($selectedPeriod)",
                                color = TextLight,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Clinical Measurements Section (Independent Scale / Section)
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E2C)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$selectedCondition Clinical Measurements (${currentData.measurementUnit})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (selectedCondition) {
                            "Diabetes" -> "Blood glucose measurements (mg/dL)"
                            "Blood Pressure" -> "Systolic & Diastolic BP measurements (mmHg)"
                            "Fever" -> "Body temperature measurements (°C/°F)"
                            "Thyroid" -> "TSH, T3, T4 hormone measurements (mIU/L)"
                            else -> "Clinical measurements"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .height(140.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No clinical health measurements recorded for this period.\n(Data unavailable)",
                            color = TextLight,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Summary Metrics Grid
            ConditionSummaryGrid(data = currentData)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MedicineDetailCard(detail: ConditionMedicineDetail) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3D)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        text = "Dosage: ${detail.dosage}",
                        color = MedicalGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Composition: ${detail.chemicalComposition}",
                style = MaterialTheme.typography.labelSmall,
                color = TextLight
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Prescribed: ${detail.prescribedDoses}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Text("Taken: ${detail.takenDoses}", style = MaterialTheme.typography.bodySmall, color = MedicalGreen)
                Text("Missed: ${detail.missedDoses}", style = MaterialTheme.typography.bodySmall, color = Error)
            }
            if (detail.timings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Timings: ${detail.timings.joinToString(" • ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MedicalAccent
                )
            }
        }
    }
}

@Composable
fun ConditionSummaryGrid(data: ConditionAnalysisData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryMetricBox(
                title = "Total Prescribed",
                value = "${data.totalPrescribed} Doses",
                icon = Icons.Default.MedicalServices,
                tint = Indigo,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricBox(
                title = "Total Taken",
                value = "${data.totalTaken} Doses",
                icon = Icons.Default.CheckCircle,
                tint = MedicalGreen,
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryMetricBox(
                title = "Missed Doses",
                value = "${data.totalMissed} Doses",
                icon = Icons.Default.Cancel,
                tint = Error,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricBox(
                title = "Adherence Rate",
                value = "${data.adherencePercentage.toInt()}%",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                tint = MedicalAccent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SummaryMetricBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier
) {
    NeumorphicCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextLight)
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
