package com.smartmedguardian.ui.screens.details

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.data.entity.MedicineInfo
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.SafetyViewModel

@Composable
fun MedicineDetailsScreen(
    medicineName: String?,
    onBackClick: () -> Unit,
    viewModel: SafetyViewModel = hiltViewModel()
) {
    val medicineInfo by viewModel.currentMedicineInfo.collectAsState()
    val duplicateAlert by viewModel.duplicateDoseAlert.collectAsState()
    val allergyAlert by viewModel.allergyAlert.collectAsState()
    val interactions by viewModel.interactions.collectAsState()

    LaunchedEffect(medicineName) {
        medicineName?.let { viewModel.fetchMedicineInfo(it) }
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
                        text = "Medicine Details",
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
            medicineInfo?.let { info ->
                MedicineInfoContent(info, duplicateAlert, interactions, allergyAlert)
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MedicineInfoContent(info: MedicineInfo, duplicateAlert: String?, interactions: List<com.smartmedguardian.data.entity.DrugInteraction>, allergyAlert: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Allergy Alert
        allergyAlert?.let {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        // Duplicate Alert
        duplicateAlert?.let {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PriorityHigh, null, tint = Warning)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Interaction Alerts
        if (interactions.isNotEmpty()) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Interaction Detected!", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(text = "Check with your doctor before taking with: ${interactions.joinToString { it.medicineB }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        // Header
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = info.medicineName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Generic: ${info.genericName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Category, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = info.category, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Details sections
        DetailSection(title = "Uses", content = info.uses, icon = Icons.Default.Healing)
        DetailSection(title = "Side Effects", content = info.commonSideEffects, icon = Icons.Default.Warning)
        DetailSection(title = "Storage", content = info.storageInstructions, icon = Icons.Default.Inventory)
        DetailSection(title = "Warnings", content = info.seriousSideEffects, icon = Icons.Default.Report, color = MaterialTheme.colorScheme.error)

        // Manufacturer Info
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Manufacturer", style = MaterialTheme.typography.labelSmall)
            Text(text = info.manufacturer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    var expanded by remember { mutableStateOf(false) }

    NeumorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
