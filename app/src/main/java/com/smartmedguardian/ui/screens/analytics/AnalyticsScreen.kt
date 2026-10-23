package com.smartmedguardian.ui.screens.analytics

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
import kotlinx.coroutines.Dispatchers
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
