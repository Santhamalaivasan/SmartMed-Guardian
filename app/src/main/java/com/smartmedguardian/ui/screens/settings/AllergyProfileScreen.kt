package com.smartmedguardian.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.data.entity.UserAllergy
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.viewmodel.SafetyViewModel

@Composable
fun AllergyProfileScreen(
    onBackClick: () -> Unit,
    viewModel: SafetyViewModel = hiltViewModel()
) {
    val allergies by viewModel.allergies.collectAsState()
    var newAllergy by remember { mutableStateOf("") }

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
                        text = "Allergy Profile",
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
        ) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeumorphicTextField(
                        value = newAllergy,
                        onValueChange = { newAllergy = it },
                        placeholder = "Add new allergy...",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NeumorphicIconButton(
                        onClick = {
                            if (newAllergy.isNotBlank()) {
                                viewModel.addAllergy(newAllergy)
                                newAllergy = ""
                            }
                        },
                        icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "My Allergies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allergies) { allergy ->
                    AllergyItem(allergy = allergy, onDelete = { viewModel.deleteAllergy(allergy) })
                }
            }
        }
    }
}

@Composable
fun AllergyItem(allergy: UserAllergy, onDelete: () -> Unit) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = allergy.allergen, style = MaterialTheme.typography.bodyLarge)
            NeumorphicIconButton(
                onClick = onDelete,
                icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
