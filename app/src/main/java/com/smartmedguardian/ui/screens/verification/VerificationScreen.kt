package com.smartmedguardian.ui.screens.verification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.Warning
import com.smartmedguardian.viewmodel.CameraViewModel

@Composable
fun VerificationScreen(
    viewModel: CameraViewModel,
    onBackClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSafetyAdviceClick: (String, String) -> Unit,
    onMedicineDetailsClick: (String) -> Unit
) {
    val verificationResult by viewModel.verificationResult.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            verificationResult?.let { result ->
                data class VerificationInfo(val icon: ImageVector, val title: String, val color: androidx.compose.ui.graphics.Color, val message: String)
                
                val info = when (result.status) {
                    VerificationStatus.CORRECT_MEDICINE -> VerificationInfo(Icons.Default.CheckCircle, "Medicine Verified Successfully", MaterialTheme.colorScheme.primary, result.message ?: "The detected medicine matches an active prescription. Dose marked as taken.")
                    VerificationStatus.ALREADY_VERIFIED -> VerificationInfo(Icons.Default.Info, "Dose Already Verified", Warning, result.message ?: "This dose has already been verified today. Dose was not marked as taken again.")
                    VerificationStatus.UNSCHEDULED_TIME -> VerificationInfo(Icons.Default.Schedule, "Not Scheduled Now", Warning, result.message ?: "This medicine is in your prescription, but is not scheduled for this time slot. Dose was not marked as taken.")
                    VerificationStatus.MEDICINE_NOT_FOUND -> VerificationInfo(Icons.Default.Error, "Medicine Does Not Match", MaterialTheme.colorScheme.error, result.message ?: "This medicine does not match your active prescriptions. Dose was not marked as taken.")
                    VerificationStatus.UNABLE_TO_VERIFY, VerificationStatus.UNKNOWN_MEDICINE -> VerificationInfo(Icons.Default.Warning, "Unverified Medicine", MaterialTheme.colorScheme.secondary, result.message ?: "Identification confidence or OCR cross-verification is unverified. Dose was not marked as taken.")
                }

                Icon(
                    imageVector = info.icon,
                    contentDescription = null,
                    tint = info.color,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = info.color
                )

                Text(
                    text = info.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    val detectedDisplayName = if (result.status == VerificationStatus.UNABLE_TO_VERIFY || result.status == VerificationStatus.UNKNOWN_MEDICINE) {
                        "Unable to Verify Medicine"
                    } else if (result.recognitionResult.name.equals("Dolo650", ignoreCase = true) || result.recognitionResult.name.equals("Dolo-650", ignoreCase = true)) {
                        "Dolo-650"
                    } else {
                        result.recognitionResult.name
                    }
                    Text(
                        text = "Detected: $detectedDisplayName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Confidence: ${(result.recognitionResult.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (result.prescription != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(text = "Prescription Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(text = "Dosage: ${result.prescription.dosage}")
                        Text(text = "Frequency: ${result.prescription.frequency}")
                        val scheduledSlots = mutableListOf<String>()
                        if (result.prescription.morning) scheduledSlots.add("Morning")
                        if (result.prescription.afternoon) scheduledSlots.add("Afternoon")
                        if (result.prescription.evening) scheduledSlots.add("Evening")
                        if (result.prescription.night) scheduledSlots.add("Night")
                        Text(text = "Scheduled Periods: ${if (scheduledSlots.isNotEmpty()) scheduledSlots.joinToString(", ") else "None"}")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // OCR Data (Expiry & Batch)
                ocrResult?.let { ocr ->
                    if (ocr.expiryDate != null || ocr.batchNumber != null) {
                        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Packaging Details", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            ocr.expiryDate?.let { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Expiry: $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            ocr.batchNumber?.let { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Tag, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Batch: $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Safety Actions
                NeumorphicButton(
                    text = "View Safety Advice",
                    onClick = { onSafetyAdviceClick(result.recognitionResult.name, result.status.name) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.Shield, null) }
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                NeumorphicButton(
                    text = "Detailed Medicine Info",
                    onClick = { onMedicineDetailsClick(result.recognitionResult.name) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.Info, null) }
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NeumorphicButton(
                        text = "Close",
                        onClick = onBackClick,
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicButton(
                        text = "History",
                        onClick = onHistoryClick,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        textColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
