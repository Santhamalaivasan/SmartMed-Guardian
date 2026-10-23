package com.smartmedguardian.ui.screens.caregiver

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.CaregiverViewModel

import android.os.Build
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CaregiverScreen(
    onBackClick: () -> Unit,
    onViewDashboardClick: (String) -> Unit,
    viewModel: CaregiverViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val caregiver by viewModel.caregiverInfo.collectAsState()
    
    val smsPermissionState = rememberPermissionState(Manifest.permission.SEND_SMS)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val postNotificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!postNotificationPermission.status.isGranted) {
                postNotificationPermission.launchPermissionRequest()
            }
        }
    }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var patientIdToMonitor by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NeumorphicIconButton(
                        onClick = onBackClick,
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Caregiver Management", style = MaterialTheme.typography.titleLarge)
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
            if (caregiver == null) {
                Text("Setup a caregiver to receive alerts if you miss critical doses.", style = MaterialTheme.typography.bodyMedium, color = TextLight)
                
                NeumorphicTextField(value = name, onValueChange = { name = it }, placeholder = "Full Name")
                NeumorphicTextField(value = relation, onValueChange = { relation = it }, placeholder = "Relationship (e.g. Daughter)")
                NeumorphicTextField(value = phone, onValueChange = { phone = it }, placeholder = "Phone Number")
                NeumorphicTextField(value = email, onValueChange = { email = it }, placeholder = "Email Address")
                
                NeumorphicButton(
                    text = "Save Caregiver",
                    onClick = { viewModel.saveCaregiver(name, relation, phone, email) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MedicalGreen,
                    textColor = Color.White
                )
            } else {
                val decodedName = viewModel.getDecryptedName(caregiver!!.encryptedName)
                val decodedPhone = viewModel.getDecryptedPhone(caregiver!!.encryptedPhoneNumber)

                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MedicalGreen, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(decodedName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(viewModel.getDecryptedName(caregiver!!.encryptedRelationship), style = MaterialTheme.typography.bodyMedium, color = TextLight)
                        }
                    }
                }

                // Caregiver Automatic SMS Alert Permission Card
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sms,
                                    contentDescription = null,
                                    tint = if (smsPermissionState.status.isGranted) MedicalGreen else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Automatic SMS Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (smsPermissionState.status.isGranted) {
                                Text(
                                    "✓ Active",
                                    color = MedicalGreen,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Text(
                            if (smsPermissionState.status.isGranted)
                                "Caregiver will automatically receive a text message whenever a prescribed dose is verified as taken."
                            else
                                "Grant SMS permission to automatically notify your caregiver via text message when you take your medication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLight
                        )
                        if (!smsPermissionState.status.isGranted) {
                            Spacer(modifier = Modifier.height(4.dp))
                            NeumorphicButton(
                                text = "Enable Automatic SMS Alerts",
                                onClick = { smsPermissionState.launchPermissionRequest() },
                                modifier = Modifier.fillMaxWidth(),
                                color = MedicalGreen,
                                textColor = Color.White,
                                icon = { Icon(Icons.Default.Sms, null) }
                            )
                        }
                    }
                }

                // Caregiver Alerts Status List
                val localAlerts by viewModel.localAlerts.collectAsState()
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Active Caregiver Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (localAlerts.isEmpty()) {
                            Text("No recent caregiver alerts.", style = MaterialTheme.typography.bodySmall, color = TextLight)
                        } else {
                            localAlerts.take(3).forEach { alert ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                                        .padding(10.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(alert.medicineName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(alert.eventType, style = MaterialTheme.typography.labelSmall, color = Error)
                                    }
                                    Text(alert.message, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val statusLabel = when (alert.status) {
                                            "ACKNOWLEDGED" -> "✓ Caregiver acknowledged"
                                            "SENT" -> "✓ Notification sent"
                                            else -> "✓ Alert created"
                                        }
                                        val statusColor = if (alert.acknowledged) MedicalGreen else MaterialTheme.colorScheme.primary
                                        Icon(
                                            if (alert.acknowledged) Icons.Default.CheckCircle else Icons.Default.Info,
                                            null,
                                            tint = statusColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Send Test Caregiver Alert Section
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Send Test Caregiver Alert", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Simulate a medication alert event to verify FCM notification and acknowledgment.", style = MaterialTheme.typography.bodySmall, color = TextLight)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeumorphicButton(
                                text = "Missed Dose",
                                onClick = { viewModel.createTestAlert("MISSED_DOSE", "Dolo 650", "Missed Morning dose") },
                                modifier = Modifier.weight(1f),
                                color = Error,
                                textColor = Color.White
                            )
                            NeumorphicButton(
                                text = "Wrong Med",
                                onClick = { viewModel.createTestAlert("WRONG_MEDICINE", "Unknown Pill", "Scanned unprescribed medicine") },
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFFF9800),
                                textColor = Color.White
                            )
                        }
                    }
                }

                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Monitor Remote Patient", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    NeumorphicTextField(
                        value = patientIdToMonitor,
                        onValueChange = { patientIdToMonitor = it },
                        placeholder = "Patient User ID (UID)"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NeumorphicButton(
                        text = "View Remote Dashboard",
                        onClick = { 
                            if (patientIdToMonitor.isNotBlank()) {
                                onViewDashboardClick(patientIdToMonitor)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MedicalGreen,
                        textColor = Color.White,
                        icon = { Icon(Icons.Default.Analytics, null) }
                    )
                }

                NeumorphicButton(
                    text = "View My Local Dashboard",
                    onClick = { onViewDashboardClick("") }, // Empty means local
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    textColor = Color.White,
                    icon = { Icon(Icons.Default.Dashboard, null) }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeumorphicButton(
                        text = "SMS Alert",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:$decodedPhone")
                                putExtra("sms_body", "Alert from SmartMed Guardian: $decodedName, the user has missed multiple critical doses.")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.Sms, null) }
                    )
                    NeumorphicButton(
                        text = "Email",
                        onClick = { /* Email intent */ },
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.Email, null) }
                    )
                }

                NeumorphicButton(
                    text = "Remove Caregiver",
                    onClick = { viewModel.deleteCaregiver() },
                    modifier = Modifier.fillMaxWidth(),
                    color = Error,
                    textColor = Color.White
                )
            }
        }
    }
}

@Composable
fun AlertPreviewCard(caregiverName: String) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Text("Alert Preview", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                .padding(12.dp)
        ) {
            Text(
                "Hi $caregiverName, SmartMed Guardian detected a HIGH risk in medication adherence. Please check on the user.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
