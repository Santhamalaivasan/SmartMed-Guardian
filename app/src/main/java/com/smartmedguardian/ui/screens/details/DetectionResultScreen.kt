package com.smartmedguardian.ui.screens.details

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartmedguardian.model.VerificationResult
import com.smartmedguardian.model.VerificationSource
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.CameraUiState
import com.smartmedguardian.viewmodel.CameraViewModel

@Composable
fun DetectionResultScreen(
    viewModel: CameraViewModel,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onVerifyClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()

    var showTechDetails by remember { mutableStateOf(false) }

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
                        text = "Detection Result",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },
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
            // Captured Image
            NeumorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
            ) {
                capturedBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Medicine",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No image captured", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Result Info
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ResultAnimation"
            ) { state ->
                when (state) {
                    is CameraUiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "Analyzing Medicine...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                    }
                    is CameraUiState.Success -> {
                        val vResult = verificationResult
                        val vSource = vResult?.verificationSource ?: VerificationSource.UNCERTAIN
                        val vStatus = vResult?.status ?: VerificationStatus.UNABLE_TO_VERIFY

                        val isUncertain = vStatus == VerificationStatus.UNABLE_TO_VERIFY ||
                                vStatus == VerificationStatus.UNKNOWN_MEDICINE ||
                                vSource == VerificationSource.UNCERTAIN

                        val primaryMedicineName = when {
                            isUncertain -> "Unable to Verify Medicine"
                            vResult?.prescription != null -> vResult.prescription.medicineName
                            else -> state.result.name
                        }

                        val primaryBadgeLabel = when (vSource) {
                            VerificationSource.OCR_TEXT -> "Verified by Text (OCR)"
                            VerificationSource.REFERENCE_IMAGE -> {
                                val sim = (vResult?.recognitionResult?.confidence?.times(100))?.toInt() ?: 0
                                "Verified by Reference Images • Similarity: $sim%"
                            }
                            VerificationSource.CNN_CLASSIFIER -> {
                                val conf = (state.result.confidence * 100).toInt()
                                "Verified by Visual Classifier • Confidence: $conf%"
                            }
                            VerificationSource.UNCERTAIN -> "Uncertain Verification"
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Primary Result Card
                            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = primaryMedicineName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (isUncertain) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = primaryBadgeLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Verification Result Details
                            vResult?.let { result: VerificationResult ->
                                val statusColor = when (result.status) {
                                    VerificationStatus.CORRECT_MEDICINE -> MaterialTheme.colorScheme.primary
                                    VerificationStatus.MEDICINE_NOT_FOUND -> MaterialTheme.colorScheme.error
                                    VerificationStatus.UNSCHEDULED_TIME -> Warning
                                    VerificationStatus.ALREADY_VERIFIED, VerificationStatus.DUPLICATE_INGREDIENT -> Warning
                                    VerificationStatus.UNABLE_TO_VERIFY, VerificationStatus.UNKNOWN_MEDICINE -> Warning
                                }

                                val statusTitle = when (result.status) {
                                    VerificationStatus.CORRECT_MEDICINE -> "✅ Medicine Verified Successfully"
                                    VerificationStatus.ALREADY_VERIFIED -> "⚠️ Dose Already Verified"
                                    VerificationStatus.DUPLICATE_INGREDIENT -> "⚠️ Duplicate Active Ingredient Warning"
                                    VerificationStatus.MEDICINE_NOT_FOUND -> "⚠️ Medicine Does Not Match Prescription"
                                    VerificationStatus.UNSCHEDULED_TIME -> "⚠️ Not Scheduled Now"
                                    VerificationStatus.UNABLE_TO_VERIFY -> "⚠️ Unable to Verify Medicine"
                                    VerificationStatus.UNKNOWN_MEDICINE -> "❓ Unknown Medicine"
                                }

                                val statusDescription = result.message ?: "The medicine could not be reliably identified as a known prescription."

                                NeumorphicCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = statusColor.copy(alpha = 0.05f)
                                ) {
                                    Text(
                                        text = statusTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = statusDescription,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                    )

                                    if (result.prescription != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Dosage: ${result.prescription.dosage} | ${result.prescription.frequency}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Secondary Collapsible "Technical Details" Section
                            Spacer(modifier = Modifier.height(12.dp))
                            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showTechDetails = !showTechDetails },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Technical Details",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                        )
                                        Icon(
                                            imageVector = if (showTechDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle Technical Details",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    }

                                    AnimatedVisibility(visible = showTechDetails) {
                                        Column(modifier = Modifier.padding(top = 12.dp)) {
                                            val ocrNameText = if (vSource == VerificationSource.OCR_TEXT && vResult?.prescription != null) {
                                                "${vResult.prescription.medicineName} ✓"
                                            } else if (!ocrResult?.rawText.isNullOrBlank()) {
                                                ocrResult?.rawText?.take(50) ?: "Detected"
                                            } else {
                                                "None"
                                            }

                                            Text(
                                                text = "OCR: $ocrNameText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = "Visual CNN: ${state.result.name} (${(state.result.confidence * 100).toInt()}%)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = "Final verification: ${vSource.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                NeumorphicButton(
                                    onClick = { viewModel.retry() },
                                    text = "Scan Again",
                                    icon = { Icon(Icons.Default.Refresh, null) },
                                    modifier = Modifier.weight(1f)
                                )
                                NeumorphicButton(
                                    onClick = onBackClick,
                                    text = "Back to Home",
                                    icon = { Icon(Icons.Default.Home, null) },
                                    color = MaterialTheme.colorScheme.primary,
                                    textColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (!isUncertain) {
                                Spacer(modifier = Modifier.height(16.dp))

                                NeumorphicButton(
                                    onClick = onDetailsClick,
                                    text = "View Medicine Details",
                                    icon = { Icon(Icons.Default.Info, null) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                NeumorphicButton(
                                    onClick = onVerifyClick,
                                    text = "Detailed Verification",
                                    icon = { Icon(Icons.Default.VerifiedUser, null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    is CameraUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            NeumorphicButton(
                                onClick = { viewModel.retry() },
                                text = "Try Again"
                            )
                        }
                    }
                    is CameraUiState.Idle -> {}
                }
            }
        }
    }
}
