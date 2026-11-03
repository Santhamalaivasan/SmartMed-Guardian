package com.smartmedguardian.ui.screens.prescription

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.smartmedguardian.data.entity.MedicineReferenceImage
import com.smartmedguardian.data.entity.Prescription
import com.smartmedguardian.ui.components.NeumorphicButton
import com.smartmedguardian.ui.components.NeumorphicCard
import com.smartmedguardian.ui.components.NeumorphicFAB
import com.smartmedguardian.ui.components.NeumorphicIconButton
import com.smartmedguardian.ui.components.NeumorphicTextField
import com.smartmedguardian.worker.ReminderScheduler
import com.smartmedguardian.viewmodel.PrescriptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrescriptionScreen(
    viewModel: PrescriptionViewModel,
    onBackClick: () -> Unit
) {
    val prescriptions by viewModel.allPrescriptions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPrescription by remember { mutableStateOf<Prescription?>(null) }
    var prescriptionToDelete by remember { mutableStateOf<Prescription?>(null) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!notificationPermissionState.status.isGranted && !notificationPermissionState.status.shouldShowRationale) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
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
                        text = "My Prescriptions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        floatingActionButton = {
            NeumorphicFAB(
                onClick = {
                    editingPrescription = null
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.Add, "Add Medicine") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            confirmationMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { confirmationMessage = null }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                }
            }

            if (prescriptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No prescriptions added yet. Tap + to create one.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(prescriptions) { prescription ->
                        PrescriptionItem(
                            prescription = prescription,
                            viewModel = viewModel,
                            onEdit = {
                                editingPrescription = prescription
                                showAddDialog = true
                            },
                            onDelete = {
                                prescriptionToDelete = prescription
                            },
                            onToggleReminders = { enabled ->
                                viewModel.toggleRemindersEnabled(prescription, enabled)
                                val statusStr = if (enabled) "Reminders enabled" else "Reminders removed"
                                confirmationMessage = "${prescription.medicineName}: $statusStr"
                            }
                        )
                    }
                }
            }
        }

        prescriptionToDelete?.let { p ->
            AlertDialog(
                onDismissRequest = { prescriptionToDelete = null },
                title = {
                    Text(
                        text = "Delete prescription?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "${p.medicineName} and its reminders will be removed.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePrescription(p)
                            prescriptionToDelete = null
                            confirmationMessage = "${p.medicineName} and reminders removed."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { prescriptionToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddDialog) {
            AddMedicineDialog(
                initialPrescription = editingPrescription,
                viewModel = viewModel,
                onDismiss = {
                    showAddDialog = false
                    editingPrescription = null
                },
                onSave = { name, dosage, freq, instr, m, a, e, n, mT, aT, eT, nT, remEnabled, bf, af, start, end, imageUris ->
                    val target = editingPrescription
                    val timeSummaryList = mutableListOf<String>()
                    if (m) timeSummaryList.add(formatTimeString(mT))
                    if (a) timeSummaryList.add(formatTimeString(aT))
                    if (e) timeSummaryList.add(formatTimeString(eT))
                    if (n) timeSummaryList.add(formatTimeString(nT))
                    val timesFormatted = if (timeSummaryList.isNotEmpty()) timeSummaryList.joinToString(" and ") else "scheduled times"

                    if (target != null) {
                        viewModel.updatePrescriptionWithImages(
                            target.copy(
                                medicineName = name,
                                dosage = dosage,
                                frequency = freq,
                                instructions = instr,
                                morning = m,
                                afternoon = a,
                                evening = e,
                                night = n,
                                morningTime = mT,
                                afternoonTime = aT,
                                eveningTime = eT,
                                nightTime = nT,
                                remindersEnabled = remEnabled,
                                beforeFood = bf,
                                afterFood = af,
                                startDate = start,
                                endDate = end
                            ),
                            imageUris
                        )
                        confirmationMessage = if (remEnabled) {
                            "Prescription updated. Reminders set for $timesFormatted."
                        } else {
                            "Prescription updated. Reminders are OFF."
                        }
                    } else {
                        viewModel.addPrescriptionWithImages(
                            Prescription(
                                medicineName = name,
                                dosage = dosage,
                                frequency = freq,
                                instructions = instr,
                                morning = m,
                                afternoon = a,
                                evening = e,
                                night = n,
                                morningTime = mT,
                                afternoonTime = aT,
                                eveningTime = eT,
                                nightTime = nT,
                                remindersEnabled = remEnabled,
                                beforeFood = bf,
                                afterFood = af,
                                startDate = start,
                                endDate = end
                            ),
                            imageUris
                        )
                        confirmationMessage = if (remEnabled) {
                            "Prescription saved. Reminders set for $timesFormatted."
                        } else {
                            "Prescription saved."
                        }
                    }
                    showAddDialog = false
                    editingPrescription = null
                }
            )
        }
    }
}

private fun formatTimeString(time24: String): String {
    val (h, m) = ReminderScheduler.parseHourMinute(time24, 8)
    return ReminderScheduler.format12HourTime(h, m)
}

@Composable
fun PrescriptionItem(
    prescription: Prescription,
    viewModel: PrescriptionViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleReminders: (Boolean) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val referenceImages by viewModel.getReferenceImages(prescription.id).collectAsState()

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prescription.medicineName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (prescription.dosage.isNotBlank()) {
                        Text(
                            text = "Dosage: ${prescription.dosage}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Row {
                    NeumorphicIconButton(
                        onClick = onEdit,
                        icon = {
                            Icon(
                                Icons.Default.Edit,
                                "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NeumorphicIconButton(
                        onClick = onDelete,
                        icon = {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            HorizontalDivider()

            // Reminder Toggle Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (prescription.remindersEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = "Reminder Icon",
                        tint = if (prescription.remindersEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (prescription.remindersEnabled) "Reminders ON" else "Reminders OFF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (prescription.remindersEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = prescription.remindersEnabled,
                    onCheckedChange = onToggleReminders
                )
            }

            // Time Slots List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (prescription.morning) {
                    TimeSlotRow("Morning", prescription.morningTime)
                }
                if (prescription.afternoon) {
                    TimeSlotRow("Afternoon", prescription.afternoonTime)
                }
                if (prescription.evening) {
                    TimeSlotRow("Evening", prescription.eveningTime)
                }
                if (prescription.night) {
                    TimeSlotRow("Night", prescription.nightTime)
                }
                if (!prescription.morning && !prescription.afternoon && !prescription.evening && !prescription.night) {
                    Text(
                        text = "No scheduled times set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (prescription.beforeFood || prescription.afterFood) {
                Row {
                    if (prescription.beforeFood) ScheduleBadge("Before Food", true, MaterialTheme.colorScheme.secondary)
                    if (prescription.afterFood) ScheduleBadge("After Food", true, MaterialTheme.colorScheme.tertiary)
                }
            }

            if (prescription.instructions.isNotBlank()) {
                Text(
                    text = "Instructions: ${prescription.instructions}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }

            if (prescription.startDate > 0 && prescription.endDate > 0) {
                Text(
                    text = "Duration: ${dateFormat.format(Date(prescription.startDate))} - ${dateFormat.format(Date(prescription.endDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (referenceImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reference Photos (${referenceImages.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(referenceImages) { ref ->
                        FilePathPreview(filePath = ref.imagePath, modifier = Modifier.size(50.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TimeSlotRow(periodLabel: String, time24: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "$periodLabel — ${formatTimeString(time24)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ScheduleBadge(text: String, active: Boolean, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) color else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddMedicineDialog(
    initialPrescription: Prescription? = null,
    viewModel: PrescriptionViewModel,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        dosage: String,
        frequency: String,
        instructions: String,
        morning: Boolean,
        afternoon: Boolean,
        evening: Boolean,
        night: Boolean,
        morningTime: String,
        afternoonTime: String,
        eveningTime: String,
        nightTime: String,
        remindersEnabled: Boolean,
        beforeFood: Boolean,
        afterFood: Boolean,
        startDate: Long,
        endDate: Long,
        imageUris: List<Uri>
    ) -> Unit
) {
    val context = LocalContext.current

    var name by remember(initialPrescription) { mutableStateOf(initialPrescription?.medicineName ?: "") }
    var dosage by remember(initialPrescription) { mutableStateOf(initialPrescription?.dosage ?: "") }
    var frequency by remember(initialPrescription) { mutableStateOf(initialPrescription?.frequency ?: "") }
    var instructions by remember(initialPrescription) { mutableStateOf(initialPrescription?.instructions ?: "") }

    var morning by remember(initialPrescription) { mutableStateOf(initialPrescription?.morning ?: false) }
    var afternoon by remember(initialPrescription) { mutableStateOf(initialPrescription?.afternoon ?: false) }
    var evening by remember(initialPrescription) { mutableStateOf(initialPrescription?.evening ?: false) }
    var night by remember(initialPrescription) { mutableStateOf(initialPrescription?.night ?: false) }

    var morningTime by remember(initialPrescription) { mutableStateOf(initialPrescription?.morningTime ?: "08:00") }
    var afternoonTime by remember(initialPrescription) { mutableStateOf(initialPrescription?.afternoonTime ?: "13:00") }
    var eveningTime by remember(initialPrescription) { mutableStateOf(initialPrescription?.eveningTime ?: "20:00") }
    var nightTime by remember(initialPrescription) { mutableStateOf(initialPrescription?.nightTime ?: "22:00") }

    var remindersEnabled by remember(initialPrescription) { mutableStateOf(initialPrescription?.remindersEnabled ?: true) }

    var beforeFood by remember(initialPrescription) { mutableStateOf(initialPrescription?.beforeFood ?: false) }
    var afterFood by remember(initialPrescription) { mutableStateOf(initialPrescription?.afterFood ?: false) }

    val defaultStartDate = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val defaultEndDate = remember {
        Calendar.getInstance().apply {
            add(Calendar.YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    var startDate by remember(initialPrescription) {
        mutableLongStateOf(initialPrescription?.startDate ?: defaultStartDate)
    }
    var endDate by remember(initialPrescription) {
        mutableLongStateOf(initialPrescription?.endDate ?: defaultEndDate)
    }

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val existingImages by if (initialPrescription != null) {
        viewModel.getReferenceImages(initialPrescription.id).collectAsState()
    } else {
        remember { mutableStateOf(emptyList<MedicineReferenceImage>()) }
    }

    var nameError by remember { mutableStateOf(false) }
    var scheduleError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val updatedList = selectedImageUris.toMutableList()
            uris.forEach { uri ->
                if (updatedList.size < 5 && !updatedList.contains(uri)) {
                    updatedList.add(uri)
                }
            }
            selectedImageUris = updatedList
            photoError = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (initialPrescription == null) "Add New Medicine" else "Edit Medicine",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // STEP 1: Medicine Name
                Text(
                    text = "1. Medicine Name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                NeumorphicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    placeholder = "e.g. Dolo-650"
                )
                if (nameError) {
                    Text(
                        text = "Medicine name is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                NeumorphicTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    placeholder = "Dosage in mg"
                )

                // STEP 2 & 3: When do you take this medicine?
                Text(
                    text = "2. When do you take this medicine?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (scheduleError) {
                    Text(
                        text = "Please select at least one medication time.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PeriodTimeSelector(
                        label = "Morning",
                        checked = morning,
                        timeStr = morningTime,
                        onCheckedChange = {
                            morning = it
                            scheduleError = false
                        },
                        onTimeClick = {
                            val (h, m) = ReminderScheduler.parseHourMinute(morningTime, 8)
                            TimePickerDialog(context, { _, hourOfDay, minute ->
                                morningTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            }, h, m, false).show()
                        }
                    )

                    PeriodTimeSelector(
                        label = "Afternoon",
                        checked = afternoon,
                        timeStr = afternoonTime,
                        onCheckedChange = {
                            afternoon = it
                            scheduleError = false
                        },
                        onTimeClick = {
                            val (h, m) = ReminderScheduler.parseHourMinute(afternoonTime, 13)
                            TimePickerDialog(context, { _, hourOfDay, minute ->
                                afternoonTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            }, h, m, false).show()
                        }
                    )

                    PeriodTimeSelector(
                        label = "Evening",
                        checked = evening,
                        timeStr = eveningTime,
                        onCheckedChange = {
                            evening = it
                            scheduleError = false
                        },
                        onTimeClick = {
                            val (h, m) = ReminderScheduler.parseHourMinute(eveningTime, 20)
                            TimePickerDialog(context, { _, hourOfDay, minute ->
                                eveningTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            }, h, m, false).show()
                        }
                    )

                    PeriodTimeSelector(
                        label = "Night",
                        checked = night,
                        timeStr = nightTime,
                        onCheckedChange = {
                            night = it
                            scheduleError = false
                        },
                        onTimeClick = {
                            val (h, m) = ReminderScheduler.parseHourMinute(nightTime, 22)
                            TimePickerDialog(context, { _, hourOfDay, minute ->
                                nightTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            }, h, m, false).show()
                        }
                    )
                }

                // STEP 4: Reminders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Reminders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { remindersEnabled = it }
                    )
                }

                // Food Timing
                Text(
                    text = "Food Timing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = beforeFood, onClick = {
                            beforeFood = !beforeFood
                            if (beforeFood) afterFood = false
                        })
                        Text("Before Food", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = afterFood, onClick = {
                            afterFood = !afterFood
                            if (afterFood) beforeFood = false
                        })
                        Text("After Food", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Duration Dates
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeumorphicButton(
                        text = "Start: ${dateFormat.format(Date(startDate))}",
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicButton(
                        text = "End: ${dateFormat.format(Date(endDate))}",
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (dateError != null) {
                    Text(
                        text = dateError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                // Reference Photos
                Text(
                    text = "Reference Photos (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeumorphicButton(
                        text = "Camera",
                        onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                showCameraDialog = true
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicButton(
                        text = "Upload Image",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedImageUris.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(selectedImageUris) { uri ->
                            ImageUriPreview(
                                uri = uri,
                                onRemove = {
                                    selectedImageUris = selectedImageUris.filter { it != uri }
                                }
                            )
                        }
                    }
                } else if (initialPrescription != null && existingImages.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(existingImages) { ref ->
                            FilePathPreview(filePath = ref.imagePath, modifier = Modifier.size(70.dp))
                        }
                    }
                }

                // Confirmation / Schedule Preview Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Your Medication Schedule",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (name.isNotBlank()) name else "Medicine Name",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (morning) Text("• Morning: ${formatTimeString(morningTime)}")
                        if (afternoon) Text("• Afternoon: ${formatTimeString(afternoonTime)}")
                        if (evening) Text("• Evening: ${formatTimeString(eveningTime)}")
                        if (night) Text("• Night: ${formatTimeString(nightTime)}")
                        if (!morning && !afternoon && !evening && !night) {
                            Text("• No times selected yet", color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            text = if (remindersEnabled) "🔔 Daily reminders enabled" else "🔕 Reminders disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remindersEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeumorphicButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicButton(
                        text = "Save Prescription",
                        onClick = {
                            if (name.isBlank()) {
                                nameError = true
                            } else if (!morning && !afternoon && !evening && !night) {
                                scheduleError = true
                            } else {
                                val startLocal = Calendar.getInstance().apply {
                                    timeInMillis = startDate
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                val endLocal = Calendar.getInstance().apply {
                                    timeInMillis = endDate
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }.timeInMillis

                                if (endLocal < startLocal) {
                                    dateError = "End date cannot be earlier than start date"
                                } else {
                                    onSave(
                                        name.trim(), dosage.trim(), frequency.trim(), instructions.trim(),
                                        morning, afternoon, evening, night,
                                        morningTime, afternoonTime, eveningTime, nightTime,
                                        remindersEnabled, beforeFood, afterFood, startLocal, endLocal,
                                        selectedImageUris
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colorScheme.primary,
                        textColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showCameraDialog) {
        ReferenceCameraDialog(
            onDismiss = { showCameraDialog = false },
            onPhotoCaptured = { capturedUri ->
                if (selectedImageUris.size < 5 && !selectedImageUris.contains(capturedUri)) {
                    selectedImageUris = selectedImageUris + capturedUri
                    photoError = null
                }
                if (selectedImageUris.size >= 5) {
                    showCameraDialog = false
                }
            },
            onUploadClicked = {
                showCameraDialog = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        val startLocal = Calendar.getInstance().apply {
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH),
                                0, 0, 0
                            )
                            set(Calendar.MILLISECOND, 0)
                        }
                        startDate = startLocal.timeInMillis
                        dateError = null
                    }
                    showStartDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        val endLocal = Calendar.getInstance().apply {
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH),
                                23, 59, 59
                            )
                            set(Calendar.MILLISECOND, 999)
                        }
                        endDate = endLocal.timeInMillis
                        dateError = null
                    }
                    showEndDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun PeriodTimeSelector(
    label: String,
    checked: Boolean,
    timeStr: String,
    onCheckedChange: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (checked) {
                Button(
                    onClick = onTimeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Pick Time",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatTimeString(timeStr),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun ReferenceCameraDialog(
    onDismiss: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onUploadClicked: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().setFlashMode(flashMode).build() }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }

    LaunchedEffect(lensFacing, flashMode) {
        val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({
                    continuation.resume(future.get())
                }, ContextCompat.getMainExecutor(context))
            }
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture.flashMode = flashMode

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            cameraControl = camera.cameraControl
        } catch (e: Exception) {
            Log.e("ReferenceCameraDialog", "Use case binding failed", e)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                cameraControl?.startFocusAndMetering(action)
                            }
                        )
                    }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeumorphicIconButton(
                    onClick = onDismiss,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                )

                NeumorphicIconButton(
                    onClick = {
                        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (flashMode == ImageCapture.FLASH_MODE_ON) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeumorphicIconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                )

                NeumorphicFAB(
                    onClick = {
                        captureReferencePhoto(context, imageCapture, cameraExecutor) { uri ->
                            coroutineScope.launch(Dispatchers.Main) {
                                onPhotoCaptured(uri)
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Capture Photo",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                )

                NeumorphicIconButton(
                    onClick = onUploadClicked,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Upload Image",
                            tint = Color.White
                        )
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

private fun captureReferencePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onPhotoCaptured: (Uri) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        "ref_cap_${System.currentTimeMillis()}.jpg"
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                Log.d("ReferenceCameraDialog", "Captured photo saved: $savedUri")
                onPhotoCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("ReferenceCameraDialog", "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}

@Composable
fun ImageUriPreview(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmapState by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmapState = bitmap?.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bitmapState?.let { imageBitmap ->
            Image(
                bitmap = imageBitmap,
                contentDescription = "Reference Photo Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun FilePathPreview(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var bitmapState by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(filePath)
                bitmapState = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bitmapState?.let { imageBitmap ->
            Image(
                bitmap = imageBitmap,
                contentDescription = "Reference Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
