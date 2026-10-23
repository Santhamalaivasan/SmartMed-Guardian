package com.smartmedguardian.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.data.entity.MedicineHistory
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBackClick: () -> Unit
) {
    val scanHistoryItems by viewModel.scanHistory.collectAsState()
    val adherenceItems by viewModel.adherenceHistory.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredAdherence = remember(adherenceItems, searchQuery) {
        if (searchQuery.isBlank()) adherenceItems
        else adherenceItems.filter { 
            it.medicineName.contains(searchQuery, ignoreCase = true) || it.status.contains(searchQuery, ignoreCase = true) 
        }
    }

    val filteredScan = remember(scanHistoryItems, searchQuery) {
        if (searchQuery.isBlank()) scanHistoryItems
        else scanHistoryItems.filter { 
            it.detectedMedicineName.contains(searchQuery, ignoreCase = true) || it.verificationStatus.contains(searchQuery, ignoreCase = true) 
        }
    }

    Scaffold(
        topBar = {
            Column {
                Box(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeumorphicIconButton(
                                onClick = onBackClick,
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "History",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        
                        if (selectedTab == 1) {
                            NeumorphicIconButton(
                                onClick = { viewModel.clearHistory() },
                                icon = {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        "Clear Scan Logs",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Medication Intake Log", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Scan Logs", fontWeight = FontWeight.Bold) }
                    )
                }
                
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    NeumorphicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = if (selectedTab == 0) "Search intake log..." else "Search scan logs...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (selectedTab == 0) {
            if (filteredAdherence.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = if (searchQuery.isEmpty()) "No intake records found." else "No matches found.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredAdherence) { record ->
                        AdherenceLogItem(record = record)
                    }
                }
            }
        } else {
            if (filteredScan.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = if (searchQuery.isEmpty()) "No scan history found." else "No matches found.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredScan) { history ->
                        HistoryItem(history = history)
                    }
                }
            }
        }
    }
}

@Composable
fun AdherenceLogItem(record: AdherenceRecord) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val statusColor = when (record.status.uppercase()) {
        "ON_TIME", "TAKEN" -> MedicalGreen
        "LATE" -> Warning
        "MISSED" -> Error
        else -> MaterialTheme.colorScheme.secondary
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.medicineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = record.status.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scheduled: ${dateFormat.format(Date(record.scheduledTime))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            if (record.actualTime != null && record.actualTime > 0) {
                Text(
                    text = "Actual: ${dateFormat.format(Date(record.actualTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            if (record.delayMinutes > 0) {
                Text(
                    text = "Delay: ${record.delayMinutes} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
            }
        }
    }
}

@Composable
fun HistoryItem(history: MedicineHistory) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = history.detectedMedicineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = history.verificationStatus.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = when(history.verificationStatus) {
                        "CORRECT_MEDICINE" -> MaterialTheme.colorScheme.primary
                        "MEDICINE_NOT_FOUND" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateFormat.format(Date(history.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "Confidence: ${(history.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}
