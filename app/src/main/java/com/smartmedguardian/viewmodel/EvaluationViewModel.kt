package com.smartmedguardian.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.data.dao.AdherenceDao
import com.smartmedguardian.data.entity.AdherenceRecord
import com.smartmedguardian.ml.MedicineClassifier
import com.smartmedguardian.ml.MedicineEmbeddingHelper
import com.smartmedguardian.ml.MedicineReferenceMatcher
import com.smartmedguardian.model.EvaluationMetrics
import com.smartmedguardian.model.EvaluationTestCase
import com.smartmedguardian.model.VerificationSource
import com.smartmedguardian.util.EvaluationRunner
import com.smartmedguardian.util.MedicineVerificationManager
import com.smartmedguardian.util.TextRecognitionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class TestFilter {
    ALL,
    MATCH,
    MISMATCH,
    EXECUTED,
    UNEXECUTED,
    TP,
    TN,
    FP,
    FN
}

@HiltViewModel
class EvaluationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verificationManager: MedicineVerificationManager,
    private val textRecognitionHelper: TextRecognitionHelper,
    private val classifier: MedicineClassifier,
    private val embeddingHelper: MedicineEmbeddingHelper,
    private val referenceMatcher: MedicineReferenceMatcher,
    private val adherenceDao: AdherenceDao
) : ViewModel() {

    private val _testCases = MutableStateFlow<List<EvaluationTestCase>>(emptyList())
    val testCases: StateFlow<List<EvaluationTestCase>> = _testCases.asStateFlow()

    private val _metrics = MutableStateFlow(EvaluationMetrics())
    val metrics: StateFlow<EvaluationMetrics> = _metrics.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _filter = MutableStateFlow(TestFilter.ALL)
    val filter: StateFlow<TestFilter> = _filter.asStateFlow()

    private var realAdherenceRecords: List<AdherenceRecord> = emptyList()

    init {
        // Load default 100 evaluation cases (50 MATCH, 50 MISMATCH)
        _testCases.value = EvaluationRunner.generateDefaultTestCases()
        
        // Listen to real adherence records from Room DB
        viewModelScope.launch {
            adherenceDao.getAllAdherenceRecords().collectLatest { records ->
                realAdherenceRecords = records
                recalculateMetrics()
            }
        }
    }

    fun setFilter(newFilter: TestFilter) {
        _filter.value = newFilter
    }

    fun runAllTestCases() {
        if (_isExecuting.value) return

        viewModelScope.launch {
            _isExecuting.value = true
            val currentList = _testCases.value.toMutableList()
            val total = currentList.size
            _progress.value = 0 to total

            for (i in currentList.indices) {
                val executedCase = EvaluationRunner.executeTestCase(
                    testCase = currentList[i],
                    context = context,
                    verificationManager = verificationManager,
                    textRecognitionHelper = textRecognitionHelper,
                    classifier = classifier,
                    embeddingHelper = embeddingHelper,
                    referenceMatcher = referenceMatcher
                )
                currentList[i] = executedCase
                _testCases.value = currentList.toList()
                _progress.value = (i + 1) to total
                recalculateMetrics()
            }

            _isExecuting.value = false
        }
    }

    fun runSingleTestCase(testCaseId: Int) {
        if (_isExecuting.value) return

        viewModelScope.launch {
            _isExecuting.value = true
            val currentList = _testCases.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == testCaseId }
            if (index != -1) {
                val executedCase = EvaluationRunner.executeTestCase(
                    testCase = currentList[index],
                    context = context,
                    verificationManager = verificationManager,
                    textRecognitionHelper = textRecognitionHelper,
                    classifier = classifier,
                    embeddingHelper = embeddingHelper,
                    referenceMatcher = referenceMatcher
                )
                currentList[index] = executedCase
                _testCases.value = currentList.toList()
                recalculateMetrics()
            }
            _isExecuting.value = false
        }
    }

    fun updateTestCase(updated: EvaluationTestCase) {
        val currentList = _testCases.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            val expected = updated.prescriptionMedicine.equals(updated.scannedMedicineName, ignoreCase = true)
            val finalUpdated = updated.copy(
                expectedMatch = expected,
                executed = false,
                actualMatch = null,
                elapsedTimeMs = null
            )
            currentList[index] = finalUpdated
            _testCases.value = currentList.toList()
            recalculateMetrics()
        }
    }

    fun batchAssignImagesToAllCases(scannedImageUri: String, referenceImageUri: String? = null) {
        val currentList = _testCases.value.toMutableList()
        for (i in currentList.indices) {
            currentList[i] = currentList[i].copy(
                scannedImageUri = scannedImageUri,
                referenceImageUri = referenceImageUri ?: currentList[i].referenceImageUri,
                executed = false,
                actualMatch = null,
                elapsedTimeMs = null
            )
        }
        _testCases.value = currentList.toList()
        recalculateMetrics()
    }

    fun resetTestCases() {
        if (_isExecuting.value) return
        _testCases.value = EvaluationRunner.generateDefaultTestCases()
        recalculateMetrics()
    }

    fun addCustomTestCase(
        prescriptionMedicine: String,
        scannedMedicineName: String,
        scannedOcrText: String? = null,
        referenceImageUri: String? = null,
        scannedImageUri: String? = null
    ) {
        val currentList = _testCases.value.toMutableList()
        val nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        val expected = prescriptionMedicine.equals(scannedMedicineName, ignoreCase = true)

        val newCase = EvaluationTestCase(
            id = nextId,
            prescriptionMedicine = prescriptionMedicine,
            referenceImageUri = referenceImageUri,
            scannedMedicineName = scannedMedicineName,
            scannedImageUri = scannedImageUri,
            scannedOcrText = scannedOcrText,
            expectedMatch = expected
        )
        currentList.add(newCase)
        _testCases.value = currentList.toList()
        recalculateMetrics()
    }

    private fun recalculateMetrics() {
        val cases = _testCases.value
        val total = cases.size
        val executed = cases.filter { it.executed && it.actualMatch != null }
        val executedCount = executed.size
        val unexecutedCount = total - executedCount

        val tp = executed.count { it.expectedMatch && it.actualMatch == true }
        val tn = executed.count { !it.expectedMatch && it.actualMatch == false }
        val fp = executed.count { !it.expectedMatch && it.actualMatch == true }
        val fn = executed.count { it.expectedMatch && it.actualMatch == false }

        val accuracy = if (executedCount > 0) ((tp + tn).toDouble() / executedCount.toDouble()) * 100.0 else null
        val precision = if ((tp + fp) > 0) (tp.toDouble() / (tp + fp).toDouble()) * 100.0 else null
        val recall = if ((tp + fn) > 0) (tp.toDouble() / (tp + fn).toDouble()) * 100.0 else null
        val f1 = if (precision != null && recall != null && (precision + recall) > 0.0) {
            (2.0 * precision * recall) / (precision + recall)
        } else null

        val ocrCount = executed.count { it.verificationSource == VerificationSource.OCR_TEXT }
        val refCount = executed.count { it.verificationSource == VerificationSource.REFERENCE_IMAGE }
        val cnnCount = executed.count { it.verificationSource == VerificationSource.CNN_CLASSIFIER }
        val uncCount = executed.count { it.verificationSource == VerificationSource.UNCERTAIN || it.verificationSource == null }

        val times = executed.mapNotNull { it.elapsedTimeMs }
        val avgTime = if (times.isNotEmpty()) times.average() else null
        val minTime = if (times.isNotEmpty()) times.minOrNull() else null
        val maxTime = if (times.isNotEmpty()) times.maxOrNull() else null
        val medianTime = if (times.isNotEmpty()) {
            val sorted = times.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 0 && mid > 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
        } else null

        // Adherence calculations from real Room records
        val totalSched = realAdherenceRecords.size
        val confirmed = realAdherenceRecords.count { it.status == "TAKEN" || it.status == "ON_TIME" || it.status == "LATE" }
        val missed = realAdherenceRecords.count { it.status == "MISSED" || it.status == "SKIPPED" }
        val adhRate = if (totalSched > 0) (confirmed.toDouble() / totalSched.toDouble()) * 100.0 else null

        _metrics.value = EvaluationMetrics(
            totalTests = total,
            executedTests = executedCount,
            unexecutedTests = unexecutedCount,
            tp = tp,
            tn = tn,
            fp = fp,
            fn = fn,
            accuracyPct = accuracy,
            precisionPct = precision,
            recallPct = recall,
            f1ScorePct = f1,
            avgVerificationTimeMs = avgTime,
            minVerificationTimeMs = minTime,
            maxVerificationTimeMs = maxTime,
            medianVerificationTimeMs = medianTime,
            ocrSourceCount = ocrCount,
            refSourceCount = refCount,
            cnnSourceCount = cnnCount,
            uncertainSourceCount = uncCount,
            totalScheduledDoses = totalSched,
            confirmedDoses = confirmed,
            missedDoses = missed,
            adherenceRatePct = adhRate
        )
    }

    fun exportReport(activityContext: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val m = _metrics.value
                val cases = _testCases.value

                val reportText = StringBuilder().apply {
                    appendLine("==================================================")
                    appendLine("   SMARTMED GUARDIAN — EVALUATION REPORT")
                    appendLine("==================================================")
                    appendLine("Date                    : $dateStr")
                    appendLine("CNN Classifier Model    : medicine_model_12class.tflite")
                    appendLine("Embedding Helper Model  : medicine_embedding.tflite")
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine("PART 1: MEDICINE VERIFICATION EVALUATION METRICS")
                    appendLine("--------------------------------------------------")
                    appendLine("Total Configured Tests  : ${m.totalTests}")
                    appendLine("Total Executed Tests    : ${m.executedTests}")
                    appendLine("Total Unexecuted Tests  : ${m.unexecutedTests} (Image Required)")
                    appendLine("True Positives (TP)     : ${m.tp}")
                    appendLine("True Negatives (TN)     : ${m.tn}")
                    appendLine("False Positives (FP)    : ${m.fp}")
                    appendLine("False Negatives (FN)    : ${m.fn}")
                    appendLine()
                    appendLine("Accuracy                : ${m.accuracyPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A (Images Required)"}")
                    appendLine("Precision               : ${m.precisionPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A"}")
                    appendLine("Recall                  : ${m.recallPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A"}")
                    appendLine("F1-score                : ${m.f1ScorePct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "N/A"}")
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine("PART 2: VERIFICATION SOURCE COUNTS")
                    appendLine("--------------------------------------------------")
                    appendLine("OCR_TEXT Count          : ${m.ocrSourceCount}")
                    appendLine("REFERENCE_IMAGE Count   : ${m.refSourceCount}")
                    appendLine("CNN_CLASSIFIER Count    : ${m.cnnSourceCount}")
                    appendLine("UNCERTAIN Count         : ${m.uncertainSourceCount}")
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine("PART 3: VERIFICATION TIME METRICS")
                    appendLine("--------------------------------------------------")
                    appendLine("Average Verification Time (ms) : ${m.avgVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"}")
                    appendLine("Minimum Verification Time (ms) : ${m.minVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"}")
                    appendLine("Maximum Verification Time (ms) : ${m.maxVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"}")
                    appendLine("Median Verification Time (ms)  : ${m.medianVerificationTimeMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "N/A"}")
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine("PART 4: ADHERENCE RATE EVALUATION")
                    appendLine("--------------------------------------------------")
                    appendLine("Total Scheduled Doses   : ${m.totalScheduledDoses}")
                    appendLine("Confirmed/Taken Doses   : ${m.confirmedDoses}")
                    appendLine("Missed Doses            : ${m.missedDoses}")
                    appendLine("Adherence Rate          : ${m.adherenceRatePct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "No adherence data available"}")
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine("PART 5: CONFUSION MATRIX TABLE")
                    appendLine("--------------------------------------------------")
                    appendLine("                    Actual MATCH    Actual MISMATCH")
                    appendLine("Predicted MATCH          ${m.tp.toString().padStart(4)}            ${m.fp.toString().padStart(4)}")
                    appendLine("Predicted MISMATCH       ${m.fn.toString().padStart(4)}            ${m.tn.toString().padStart(4)}")
                    appendLine()
                    appendLine("--------------------------------------------------")
                    appendLine("PART 6: INDIVIDUAL TEST CASES BREAKDOWN")
                    appendLine("--------------------------------------------------")
                    appendLine("ID | Prescribed | Scanned | Expected | Actual | Class | Source | Time(ms) | Message")
                    cases.forEach { c ->
                        val exp = if (c.expectedMatch) "MATCH" else "MISMATCH"
                        val act = if (!c.executed) "UNEXECUTED (Image Required)" else if (c.actualMatch == true) "MATCH" else "MISMATCH"
                        val cls = c.resultClassification?.name ?: "N/A"
                        val src = c.verificationSource?.name ?: "N/A"
                        val time = c.elapsedTimeMs?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"
                        appendLine("${c.id} | ${c.prescriptionMedicine} | ${c.scannedMedicineName} | $exp | $act | $cls | $src | $time | ${c.message ?: ""}")
                    }
                    appendLine("==================================================")
                }.toString()

                val file = File(activityContext.cacheDir, "smartmed_evaluation_report.txt")
                file.writeText(reportText)

                val uri: Uri = FileProvider.getUriForFile(
                    activityContext,
                    "${activityContext.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "SmartMed Guardian Evaluation Results")
                    putExtra(Intent.EXTRA_TEXT, "SmartMed Guardian evaluation results are attached.\n\n$reportText")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, "Export Evaluation Results")

                withContext(Dispatchers.Main) {
                    try {
                        activityContext.startActivity(chooserIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            activityContext,
                            "No application found to handle export/share intent",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            activityContext,
                            "Failed to open share dialog: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("EvaluationViewModel", "Error generating evaluation report: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activityContext,
                        "Report export error: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
