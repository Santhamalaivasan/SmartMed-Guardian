package com.smartmedguardian.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*

@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onBarcodeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPrescriptionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onScheduleClick: () -> Unit
) {
    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SmartMed Guardian",
                        style = MaterialTheme.typography.titleLarge
                    )
                    NeumorphicIconButton(
                        onClick = onSettingsClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Scan your medicine safely using AI.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            
            NeumorphicButton(
                text = "Scan Medicine Box",
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.CameraAlt, null) }
            )

            NeumorphicButton(
                text = "Scan Barcode / QR",
                onClick = onBarcodeClick,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Default.QrCodeScanner, null) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeMenuButton(
                    text = "Prescriptions",
                    icon = Icons.Default.Description,
                    onClick = onPrescriptionClick,
                    modifier = Modifier.weight(1f)
                )
                HomeMenuButton(
                    text = "Schedule",
                    icon = Icons.Default.Event,
                    onClick = onScheduleClick,
                    modifier = Modifier.weight(1f)
                )
            }

            HomeMenuButton(
                text = "Medicine History",
                icon = Icons.Default.History,
                onClick = onHistoryClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HomeMenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeumorphicButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
    )
}
