package com.smartmedguardian.ui.screens.evaluation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.model.EvaluationMetrics
import com.smartmedguardian.model.EvaluationResultClass
import com.smartmedguardian.model.EvaluationTestCase
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.EvaluationViewModel
import com.smartmedguardian.viewmodel.TestFilter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEvaluationScreen(
    onBackClick: () -> Unit,
    viewModel: EvaluationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val testCases by viewModel.testCases.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingTestCase by remember { mutableStateOf<EvaluationTestCase?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val batchImageLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.batchAssignImagesToAllCases(it.toString())
            Toast.makeText(context, "Scanned image attached to all 100 test cases!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Medicine Evaluation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Image-Based Verification Metrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportReport(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Results")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeumorphicButton(
                        text = if (isExecuting) "Running (${progress.first}/${progress.second})..." else "Run All Tests",
                        onClick = {
                            if (!isExecuting) {
                                val casesWithImages = testCases.count { it.hasValidImageInput }
                                if (casesWithImages == 0) {
                                    Toast.makeText(
                                        context,
                                        "No images attached! Select a scanned image or tap 'Batch Set Image' to run evaluation.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                viewModel.runAllTestCases()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        color = MedicalGreen,
                        textColor = Color.White,
                        icon = {
                            if (isExecuting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                            }
                        }
                    )

                    NeumorphicIconButton(
                        onClick = { if (!isExecuting) viewModel.resetTestCases() },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "Reset Tests") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Execution Progress Bar
            if (isExecuting) {
                LinearProgressIndicator(
                    progress = { if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MedicalGreen,
                    trackColor = MedicalGreen.copy(alpha = 0.2f)
                )
            }

            // Tab Selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Results & Metrics") },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Test Cases (${testCases.size})") },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> EvaluationResultsTab(metrics = metrics, onExportClick = { viewModel.exportReport(context) })
                1 -> TestCasesTab(
                    testCases = testCases,
                    activeFilter = filter,
                    onFilterSelect = { viewModel.setFilter(it) },
                    onRunSingle = { viewModel.runSingleTestCase(it) },
                    onEditClick = { editingTestCase = it },
                    onAddClick = { showAddDialog = true },
                    onBatchSetImage = { batchImageLauncher.launch("image/*") }
                )
            }
        }
    }

    // Edit Test Case Dialog
    editingTestCase?.let { tc ->
        EditTestCaseDialog(
            testCase = tc,
            onDismiss = { editingTestCase = null },
            onSave = { updated ->
                viewModel.updateTestCase(updated)
                editingTestCase = null
            }
        )
    }

    // Add Custom Test Case Dialog
    if (showAddDialog) {
        AddTestCaseDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { pres, scanned, ocr, refUri, scanUri ->
                viewModel.addCustomTestCase(pres, scanned, ocr, refUri, scanUri)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun EvaluationResultsTab(
    metrics: EvaluationMetrics,
    onExportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // MEDICINE VERIFICATION EVALUATION HEADER CARD
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEDICINE VERIFICATION EVALUATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = if (metrics.executedTests > 0) MedicalGreen.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${metrics.executedTests} Executed / ${metrics.unexecutedTests} Image Required",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (metrics.executedTests > 0) MedicalGreen else Error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider()

                // TP, TN, FP, FN Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCountCard("True Positives", "${metrics.tp}", MedicalGreen, Modifier.weight(1f))
                    MetricCountCard("True Negatives", "${metrics.tn}", Indigo, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCountCard("False Positives", "${metrics.fp}", Error, Modifier.weight(1f))
                    MetricCountCard("False Negatives", "${metrics.fn}", Warning, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calculated Metrics
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricValueRow("Accuracy", metrics.accuracyPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A (Images Required)")
                    MetricValueRow("Precision", metrics.precisionPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A")
                    MetricValueRow("Recall", metrics.recallPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A")
                    MetricValueRow("F1-score", metrics.f1ScorePct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A")
                }
            }
        }

        // VERIFICATION SOURCE COUNTS CARD
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "REAL VERIFICATION SOURCE COUNTS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceCountBox("OCR_TEXT", "${metrics.ocrSourceCount}", MedicalGreen, Modifier.weight(1f))
                    SourceCountBox("REFERENCE", "${metrics.refSourceCount}", Indigo, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceCountBox("CNN MODEL", "${metrics.cnnSourceCount}", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    SourceCountBox("UNCERTAIN", "${metrics.uncertainSourceCount}", Warning, Modifier.weight(1f))
                }
            }
        }

        // CONFUSION MATRIX CARD
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "CONFUSION MATRIX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                ConfusionMatrixTable(
                    tp = metrics.tp,
                    fp = metrics.fp,
                    fn = metrics.fn,
                    tn = metrics.tn
                )
            }
        }

        // VERIFICATION TIME CARD
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "VERIFICATION TIME",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                MetricValueRow(
                    label = "Average Verification Time (ms)",
                    value = metrics.avgVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A",
                    highlight = true
                )
                MetricValueRow(
                    label = "Minimum Verification Time",
                    value = metrics.minVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"
                )
                MetricValueRow(
                    label = "Maximum Verification Time",
                    value = metrics.maxVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"
                )
                MetricValueRow(
                    label = "Median Verification Time",
                    value = metrics.medianVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"
                )
            }
        }

        // ADHERENCE EVALUATION CARD
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ADHERENCE EVALUATION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                MetricValueRow("Scheduled Doses", "${metrics.totalScheduledDoses}")
                MetricValueRow("Confirmed/Taken Doses", "${metrics.confirmedDoses}")
                MetricValueRow("Missed Doses", "${metrics.missedDoses}")
                
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adherence Rate",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = metrics.adherenceRatePct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "No adherence data available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (metrics.adherenceRatePct != null) MedicalGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // MODEL INFORMATION CARD
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MODEL INFORMATION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text("CNN Model: medicine_model_12class.tflite", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text("Embedding Model: medicine_embedding.tflite", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Verification Pipeline Rules:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("• Runs real ML Kit OCR, TFLite CNN & MobileNet embedding matcher.", style = MaterialTheme.typography.bodySmall)
                Text("• Requires real image inputs for valid test execution.", style = MaterialTheme.typography.bodySmall)
                Text("• Unexecuted tests without images are excluded from IEEE accuracy formulas.", style = MaterialTheme.typography.bodySmall)
            }
        }

        NeumorphicButton(
            text = "Export Evaluation Results",
            onClick = onExportClick,
            modifier = Modifier.fillMaxWidth(),
            icon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SourceCountBox(label: String, count: String, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Text(count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ConfusionMatrixTable(
    tp: Int,
    fp: Int,
    fn: Int,
    tn: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Table Header Row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1.3f))
                Text(
                    "Actual MATCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Actual MISMATCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // Row 1: Predicted MATCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Predicted MATCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.3f)
                )
                CellBox("TP: $tp", MedicalGreen, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(4.dp))
                CellBox("FP: $fp", Error, Modifier.weight(1f))
            }

            // Row 2: Predicted MISMATCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Predicted MISMATCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.3f)
                )
                CellBox("FN: $fn", Warning, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(4.dp))
                CellBox("TN: $tn", Indigo, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CellBox(text: String, accentColor: Color, modifier: Modifier) {
    Surface(
        color = accentColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
fun MetricCountCard(title: String, count: String, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Text(count, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun MetricValueRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (highlight) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TestCasesTab(
    testCases: List<EvaluationTestCase>,
    activeFilter: TestFilter,
    onFilterSelect: (TestFilter) -> Unit,
    onRunSingle: (Int) -> Unit,
    onEditClick: (EvaluationTestCase) -> Unit,
    onAddClick: () -> Unit,
    onBatchSetImage: () -> Unit
) {
    val filteredCases = remember(testCases, activeFilter) {
        when (activeFilter) {
            TestFilter.ALL -> testCases
            TestFilter.MATCH -> testCases.filter { it.expectedMatch }
            TestFilter.MISMATCH -> testCases.filter { !it.expectedMatch }
            TestFilter.EXECUTED -> testCases.filter { it.executed }
            TestFilter.UNEXECUTED -> testCases.filter { !it.executed }
            TestFilter.TP -> testCases.filter { it.resultClassification == EvaluationResultClass.TP }
            TestFilter.TN -> testCases.filter { it.resultClassification == EvaluationResultClass.TN }
            TestFilter.FP -> testCases.filter { it.resultClassification == EvaluationResultClass.FP }
            TestFilter.FN -> testCases.filter { it.resultClassification == EvaluationResultClass.FN }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips Row
        ScrollableTabRow(
            selectedTabIndex = activeFilter.ordinal,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            TestFilter.entries.forEach { filterItem ->
                Tab(
                    selected = activeFilter == filterItem,
                    onClick = { onFilterSelect(filterItem) },
                    text = { Text(filterItem.name) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing ${filteredCases.size} of ${testCases.size} test cases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Row {
                TextButton(onClick = onBatchSetImage) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Batch Set Image")
                }

                TextButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Custom")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredCases, key = { it.id }) { item ->
                TestCaseRowItem(
                    testCase = item,
                    onRunClick = { onRunSingle(item.id) },
                    onEditClick = { onEditClick(item) }
                )
            }
        }
    }
}

@Composable
fun TestCaseRowItem(
    testCase: EvaluationTestCase,
    onRunClick: () -> Unit,
    onEditClick: () -> Unit
) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${testCase.id}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val (expLabel, expColor) = if (testCase.expectedMatch) "EXPECTED MATCH" to MedicalGreen else "EXPECTED MISMATCH" to Error
                    Surface(
                        color = expColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = expLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = expColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Image Required Warning Badge OR Status Badge (TP / TN / FP / FN / Pending)
                if (!testCase.hasValidImageInput) {
                    Surface(
                        color = Error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Error)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Image Required",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Error
                            )
                        }
                    }
                } else {
                    val cls = testCase.resultClassification
                    if (cls != null) {
                        val color = when (cls) {
                            EvaluationResultClass.TP -> MedicalGreen
                            EvaluationResultClass.TN -> Indigo
                            EvaluationResultClass.FP -> Error
                            EvaluationResultClass.FN -> Warning
                        }
                        Surface(
                            color = color,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = cls.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text("Ready to Run", style = MaterialTheme.typography.bodySmall, color = MedicalGreen)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Prescription:", style = MaterialTheme.typography.labelSmall, color = TextLight)
                    Text(testCase.prescriptionMedicine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Scanned Target:", style = MaterialTheme.typography.labelSmall, color = TextLight)
                    Text(testCase.scannedMedicineName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (testCase.executed) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source: ${testCase.verificationSource?.name ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Time: ${testCase.elapsedTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit / Attach Images", modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onRunClick,
                    colors = ButtonDefaults.buttonColors(containerColor = if (testCase.hasValidImageInput) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run")
                }
            }
        }
    }
}

@Composable
fun EditTestCaseDialog(
    testCase: EvaluationTestCase,
    onDismiss: () -> Unit,
    onSave: (EvaluationTestCase) -> Unit
) {
    var presMed by remember { mutableStateOf(testCase.prescriptionMedicine) }
    var scannedMed by remember { mutableStateOf(testCase.scannedMedicineName) }
    var refImageUri by remember { mutableStateOf(testCase.referenceImageUri ?: "") }
    var scannedImageUri by remember { mutableStateOf(testCase.scannedImageUri ?: "") }

    val refLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { refImageUri = it.toString() }
    }

    val scanLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scannedImageUri = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Test Case #${testCase.id}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = presMed,
                    onValueChange = { presMed = it },
                    label = { Text("Prescription Medicine") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = scannedMed,
                    onValueChange = { scannedMed = it },
                    label = { Text("Scanned Medicine Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { scanLauncher.launch("image/*") }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scanned Image")
                    }
                    if (scannedImageUri.isNotBlank()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MedicalGreen)
                    } else {
                        Text("Required", color = Error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { refLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reference Image")
                    }
                    if (refImageUri.isNotBlank()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MedicalGreen)
                    } else {
                        Text("Optional", color = TextLight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    testCase.copy(
                        prescriptionMedicine = presMed,
                        scannedMedicineName = scannedMed,
                        referenceImageUri = refImageUri.ifBlank { null },
                        scannedImageUri = scannedImageUri.ifBlank { null }
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddTestCaseDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String?, String?, String?) -> Unit
) {
    var presMed by remember { mutableStateOf("Augmentin") }
    var scannedMed by remember { mutableStateOf("Augmentin") }
    var refImageUri by remember { mutableStateOf("") }
    var scannedImageUri by remember { mutableStateOf("") }

    val refLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { refImageUri = it.toString() }
    }

    val scanLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scannedImageUri = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Test Case") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = presMed,
                    onValueChange = { presMed = it },
                    label = { Text("Prescription Medicine") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = scannedMed,
                    onValueChange = { scannedMed = it },
                    label = { Text("Scanned Medicine") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { scanLauncher.launch("image/*") }) {
                        Text("Select Scanned Image")
                    }
                    if (scannedImageUri.isNotBlank()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MedicalGreen)
                    } else {
                        Text("Required", color = Error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { refLauncher.launch("image/*") }) {
                        Text("Select Reference Image")
                    }
                    if (refImageUri.isNotBlank()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MedicalGreen)
                    } else {
                        Text("Optional", color = TextLight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (presMed.isNotBlank() && scannedMed.isNotBlank()) {
                        onAdd(
                            presMed.trim(),
                            scannedMed.trim(),
                            null,
                            refImageUri.ifBlank { null },
                            scannedImageUri.ifBlank { null }
                        )
                    }
                }
            ) {
                Text("Add Case")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
