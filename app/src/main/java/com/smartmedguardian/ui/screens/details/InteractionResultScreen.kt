package com.smartmedguardian.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.data.entity.DrugInteraction
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.viewmodel.SafetyViewModel

@Composable
fun InteractionResultScreen(
    medicineName: String?,
    onBackClick: () -> Unit,
    viewModel: SafetyViewModel = hiltViewModel()
) {
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
                        text = "Drug Interactions",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (interactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "No interactions detected.", color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(interactions) { interaction ->
                    InteractionItem(interaction = interaction)
                }
            }
        }
    }
}

@Composable
fun InteractionItem(interaction: DrugInteraction) {
    val color = when(interaction.severity) {
        "High" -> MaterialTheme.colorScheme.error
        "Moderate" -> com.smartmedguardian.ui.theme.Warning
        else -> MaterialTheme.colorScheme.secondary
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row {
            Icon(Icons.Default.Warning, null, tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${interaction.medicineA} + ${interaction.medicineB}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(text = "Severity: ${interaction.severity}", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = interaction.reason, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recommendation: ${interaction.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
