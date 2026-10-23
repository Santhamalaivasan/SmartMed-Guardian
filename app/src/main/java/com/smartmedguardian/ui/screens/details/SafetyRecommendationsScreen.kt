package com.smartmedguardian.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.viewmodel.SafetyViewModel

@Composable
fun SafetyRecommendationsScreen(
    medicineName: String?,
    verificationStatus: String?,
    onBackClick: () -> Unit,
    viewModel: SafetyViewModel = hiltViewModel()
) {
    val medicineInfo by viewModel.currentMedicineInfo.collectAsState()
    
    LaunchedEffect(medicineName) {
        medicineName?.let { viewModel.fetchMedicineInfo(it) }
    }

    val status = try {
        com.smartmedguardian.model.VerificationStatus.valueOf(verificationStatus ?: "MEDICINE_NOT_FOUND")
    } catch (e: Exception) {
        com.smartmedguardian.model.VerificationStatus.MEDICINE_NOT_FOUND
    }

    val recommendations = viewModel.getRecommendations(medicineInfo, status)

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
                        text = "Safety Advice",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Personalized guidance for ${medicineName ?: "this medicine"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            
            items(recommendations) { recommendation ->
                RecommendationItem(text = recommendation)
            }
        }
    }
}

@Composable
fun RecommendationItem(text: String) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
