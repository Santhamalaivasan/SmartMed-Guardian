package com.smartmedguardian.ui.screens.caregiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smartmedguardian.data.entity.CaregiverAlert
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.CaregiverViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CaregiverDashboardScreen(
    patientId: String,
    onBackClick: () -> Unit,
    viewModel: CaregiverViewModel = hiltViewModel()
) {
    val risk by viewModel.remoteRisk.collectAsState()
    val adherenceRecords by viewModel.remoteAdherence.collectAsState()
    val localAlerts by viewModel.localAlerts.collectAsState()
    val remoteAlerts by viewModel.remoteAlerts.collectAsState()

    val alerts = if (patientId == "local" || patientId.isBlank()) localAlerts else remoteAlerts

    LaunchedEffect(patientId) {
        if (patientId != "local" && patientId.isNotBlank()) {
            viewModel.selectPatient(patientId)
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NeumorphicIconButton(
                        onClick = onBackClick,
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Caregiver Alert Dashboard", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        containerColor = Background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                risk?.let { CaregiverRiskCard(it) } ?: Text("Monitoring active patient adherence & safety", color = TextLight)
            }

            item {
                Text(
                    "Caregiver Medication Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (alerts.isEmpty()) {
                item {
                    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No active caregiver alerts recorded.", color = TextLight)
                    }
                }
            } else {
                items(alerts) { alert ->
                    CaregiverAlertItemCard(
                        alert = alert,
                        onAcknowledge = { viewModel.acknowledgeAlert(alert.alertId) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Recent Medication Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(adherenceRecords.take(5)) { record ->
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    val isTaken = record.status == "ON_TIME" || record.status == "TAKEN" || record.status == "LATE"
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(record.medicineName, fontWeight = FontWeight.Bold)
                            Text(record.status, color = if (isTaken) MedicalGreen else Error)
                        }
                        Icon(if (isTaken) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (isTaken) MedicalGreen else Error)
                    }
                }
            }
        }
    }
}

@Composable
fun CaregiverAlertItemCard(
    alert: CaregiverAlert,
    onAcknowledge: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    val formattedTime = dateFormat.format(Date(alert.createdAt))

    val (badgeColor, badgeText) = when (alert.eventType) {
        "MISSED_DOSE" -> Pair(Error, "MISSED DOSE")
        "WRONG_MEDICINE" -> Pair(Color(0xFFFF9800), "WRONG MEDICINE")
        "DUPLICATE_DOSE" -> Pair(Color(0xFF2196F3), "DUPLICATE DOSE")
        "SAFETY_WARNING" -> Pair(Error, "SAFETY WARNING")
        else -> Pair(MaterialTheme.colorScheme.primary, alert.eventType)
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(formattedTime, style = MaterialTheme.typography.bodySmall, color = TextLight)
            }

            Text(alert.medicineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(alert.message, style = MaterialTheme.typography.bodyMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when (alert.status) {
                        "ACKNOWLEDGED" -> "✓ Caregiver acknowledged"
                        "SENT" -> "✓ Notification sent"
                        else -> "✓ Alert created"
                    }
                    val statusColor = if (alert.acknowledged) MedicalGreen else MaterialTheme.colorScheme.primary
                    Icon(
                        if (alert.acknowledged) Icons.Default.CheckCircle else Icons.Default.Info,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                }

                if (alert.acknowledged) {
                    val ackFormatted = alert.acknowledgedAt?.let { dateFormat.format(Date(it)) } ?: ""
                    Text(
                        text = "Ack: $ackFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MedicalGreen
                    )
                }
            }

            if (!alert.acknowledged) {
                Spacer(modifier = Modifier.height(4.dp))
                NeumorphicButton(
                    text = "ACKNOWLEDGE",
                    onClick = onAcknowledge,
                    modifier = Modifier.fillMaxWidth(),
                    color = MedicalGreen,
                    textColor = Color.White,
                    icon = { Icon(Icons.Default.Done, null) }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MedicalGreen.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Caregiver acknowledged", color = MedicalGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun CaregiverRiskCard(risk: com.smartmedguardian.model.RiskAssessment) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth(), color = risk.level.color.copy(alpha = 0.1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HealthAndSafety, null, tint = risk.level.color, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Current Patient Risk", style = MaterialTheme.typography.labelSmall)
                Text(risk.level.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = risk.level.color)
            }
        }
    }
}
