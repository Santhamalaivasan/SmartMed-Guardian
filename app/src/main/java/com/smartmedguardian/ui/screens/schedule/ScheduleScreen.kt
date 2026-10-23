package com.smartmedguardian.ui.screens.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.worker.ReminderScheduler
import com.smartmedguardian.viewmodel.PrescriptionViewModel

@Composable
fun ScheduleScreen(
    viewModel: PrescriptionViewModel,
    onBackClick: () -> Unit,
    onVerifyDose: (Prescription, String, String) -> Unit = { _, _, _ -> }
) {
    val prescriptions by viewModel.allPrescriptions.collectAsState()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                TimeSlotSection(
                    title = "Morning",
                    medicines = prescriptions.filter { it.morning },
                    getTimeStr = { it.morningTime },
                    onMarkTaken = { medicine, timeStr -> onVerifyDose(medicine, "Morning", timeStr) },
                    onMarkSkipped = { viewModel.markDoseSkipped(it) }
                )
            }
            item {
                TimeSlotSection(
                    title = "Afternoon",
                    medicines = prescriptions.filter { it.afternoon },
                    getTimeStr = { it.afternoonTime },
                    onMarkTaken = { medicine, timeStr -> onVerifyDose(medicine, "Afternoon", timeStr) },
                    onMarkSkipped = { viewModel.markDoseSkipped(it) }
                )
            }
            item {
                TimeSlotSection(
                    title = "Evening",
                    medicines = prescriptions.filter { it.evening },
                    getTimeStr = { it.eveningTime },
                    onMarkTaken = { medicine, timeStr -> onVerifyDose(medicine, "Evening", timeStr) },
                    onMarkSkipped = { viewModel.markDoseSkipped(it) }
                )
            }
            item {
                TimeSlotSection(
                    title = "Night",
                    medicines = prescriptions.filter { it.night },
                    getTimeStr = { it.nightTime },
                    onMarkTaken = { medicine, timeStr -> onVerifyDose(medicine, "Night", timeStr) },
                    onMarkSkipped = { viewModel.markDoseSkipped(it) }
                )
            }
        }
    }
}

@Composable
fun TimeSlotSection(
    title: String,
    medicines: List<Prescription>,
    getTimeStr: (Prescription) -> String,
    onMarkTaken: (Prescription, String) -> Unit,
    onMarkSkipped: (Prescription) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (medicines.isEmpty()) {
            Text(
                text = "No medicines scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        } else {
            medicines.forEach { medicine ->
                val (h, m) = ReminderScheduler.parseHourMinute(getTimeStr(medicine), 8)
                val formattedTime = ReminderScheduler.format12HourTime(h, m)

                NeumorphicCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    cornerRadius = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = medicine.medicineName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Time: $formattedTime • ${medicine.dosage.ifBlank { "As prescribed" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row {
                            NeumorphicIconButton(
                                onClick = { onMarkTaken(medicine, formattedTime) },
                                icon = { Icon(Icons.Default.Check, "Verify & Mark Taken", tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            NeumorphicIconButton(
                                onClick = { onMarkSkipped(medicine) },
                                icon = { Icon(Icons.Default.Close, "Mark Skipped", tint = MaterialTheme.colorScheme.error) },
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
