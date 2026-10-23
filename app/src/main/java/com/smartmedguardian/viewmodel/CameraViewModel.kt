package com.smartmedguardian.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmedguardian.ml.RecognitionResult
import com.smartmedguardian.model.VerificationResult
import com.smartmedguardian.model.VerificationStatus
import com.smartmedguardian.repository.MedicineRepository
import com.smartmedguardian.util.BarcodeScannerHelper
import com.smartmedguardian.util.TextRecognitionHelper
import com.smartmedguardian.util.TextResult
import com.smartmedguardian.util.VoiceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val voiceHelper: VoiceHelper,
    private val textHelper: TextRecognitionHelper,
    private val barcodeHelper: BarcodeScannerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _verificationResult = MutableStateFlow<VerificationResult?>(null)
    val verificationResult: StateFlow<VerificationResult?> = _verificationResult.asStateFlow()

    private val _ocrResult = MutableStateFlow<TextResult?>(null)
    val ocrResult: StateFlow<TextResult?> = _ocrResult.asStateFlow()

    private var targetMedicine: String? = null
    private var targetSlot: String? = null
    private var targetTime: String? = null
    private var prescriptionId: Long = -1L

    fun setTargetContext(medicine: String?, slot: String?, time: String?, pId: Long = -1L) {
        targetMedicine = medicine?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        targetSlot = slot?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        targetTime = time?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        prescriptionId = pId
    }

    fun onImageCaptured(bitmap: Bitmap) {
        Log.d("CameraViewModel", "=== CAMERA BITMAP ===")
        Log.d("CameraViewModel", "Width: ${bitmap.width}")
        Log.d("CameraViewModel", "Height: ${bitmap.height}")
        Log.d("CameraViewModel", "Config: ${bitmap.config}")
        Log.d("CameraViewModel", "Passed directly to classifier without additional normalization or transformation.")
        Log.d("CameraViewModel", "======================")
        _capturedBitmap.value = bitmap
        processImage(bitmap)
    }

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val analysisStart = System.currentTimeMillis()
            Log.d("MedicineAnalysis", "==================================================")
            Log.d("MedicineAnalysis", "[MedicineAnalysis] Starting medicine analysis pipeline. Input: ${bitmap.width}x${bitmap.height}")
            _uiState.value = CameraUiState.Loading

            try {
                withTimeout(10_000L) {
                    // Preprocessing: Create 224x224 scaled bitmap for TFLite CNN & Embedding models
                    val prepStart = System.currentTimeMillis()
                    val scaled224 = if (bitmap.width == 224 && bitmap.height == 224) {
                        bitmap
                    } else {
                        Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                    }
                    val prepTime = System.currentTimeMillis() - prepStart
                    Log.d("MedicineAnalysis", "[MedicineAnalysis] Image preprocessing: ${prepTime} ms")

                    // Stage 1 & Stage 2: Concurrently run OCR and TFLite CNN Classification in parallel
                    val ocrDeferred = async(Dispatchers.Default) {
                        val ocrStart = System.currentTimeMillis()
                        val res = try {
                            textHelper.recognizeText(bitmap)
                        } catch (e: Exception) {
                            Log.e("MedicineAnalysis", "[OCR_FAILED] OCR failed: ${e.message}", e)
                            TextResult(null, null, "")
                        }
                        val ocrTime = System.currentTimeMillis() - ocrStart
                        Log.d("MedicineAnalysis", "[MedicineAnalysis] OCR: ${ocrTime} ms")
                        res
                    }

                    val cnnDeferred = async(Dispatchers.Default) {
                        val cnnStart = System.currentTimeMillis()
                        val res = try {
                            repository.classifyImage(scaled224).getOrElse {
                                RecognitionResult("Unknown", 0f, 0)
                            }
                        } catch (e: Exception) {
                            Log.e("MedicineAnalysis", "[CNN_FAILED] CNN classification failed: ${e.message}", e)
                            RecognitionResult("Unknown", 0f, 0)
                        }
                        val cnnTime = System.currentTimeMillis() - cnnStart
                        Log.d("MedicineAnalysis", "[MedicineAnalysis] CNN classification: ${cnnTime} ms")
                        res
                    }

                    val ocrRes = ocrDeferred.await()
                    val cnnResult = cnnDeferred.await()

                    _ocrResult.value = ocrRes

                    // Stage 3: Verification & Prescription Matching against Room DB
                    val matchStart = System.currentTimeMillis()
                    val verification = try {
                        repository.verifyRecognition(
                            recognitionResult = cnnResult,
                            ocrText = ocrRes.rawText,
                            queryBitmap = scaled224,
                            targetMedicine = targetMedicine,
                            targetSlot = targetSlot,
                            targetTime = targetTime,
                            prescriptionId = prescriptionId
                        )
                    } catch (e: Exception) {
                        Log.e("MedicineAnalysis", "[VERIFICATION_FAILED] Verification failed: ${e.message}", e)
                        VerificationResult(
                            status = VerificationStatus.UNABLE_TO_VERIFY,
                            recognitionResult = cnnResult,
                            message = "Verification failed due to database error: ${e.message}"
                        )
                    }
                    val prescTime = System.currentTimeMillis() - matchStart
                    Log.d("MedicineAnalysis", "[MedicineAnalysis] Prescription matching: ${prescTime} ms")

                    if (scaled224 !== bitmap && !scaled224.isRecycled) {
                        scaled224.recycle()
                    }

                    _verificationResult.value = verification

                    val displayResult = if (verification.status == VerificationStatus.UNABLE_TO_VERIFY || verification.status == VerificationStatus.UNKNOWN_MEDICINE) {
                        cnnResult.copy(name = "Unable to Verify Medicine")
                    } else if (cnnResult.name.equals("Dolo650", ignoreCase = true) || cnnResult.name.equals("Dolo-650", ignoreCase = true)) {
                        cnnResult.copy(name = "Dolo-650")
                    } else {
                        cnnResult
                    }

                    val totalElapsed = System.currentTimeMillis() - analysisStart
                    Log.d("MedicineAnalysis", "[MedicineAnalysis] Total: ${totalElapsed} ms")
                    Log.d("MedicineAnalysis", "==================================================")
                    _uiState.value = CameraUiState.Success(displayResult)

                    triggerVoiceGuidance(verification.status)
                }
            } catch (e: TimeoutCancellationException) {
                val totalElapsed = System.currentTimeMillis() - analysisStart
                Log.e("MedicineAnalysis", "[TIMEOUT] Analysis timed out after ${totalElapsed} ms", e)
                _uiState.value = CameraUiState.Error("Medicine analysis timed out. Please try again.")
                voiceHelper.speak("Medicine analysis timed out. Please try again.")
            } catch (e: Exception) {
                val totalElapsed = System.currentTimeMillis() - analysisStart
                Log.e("MedicineAnalysis", "[ERROR] Analysis pipeline failed after ${totalElapsed} ms: ${e.message}", e)
                _uiState.value = CameraUiState.Error("Analysis failed: ${e.localizedMessage ?: "Unknown error"}")
                voiceHelper.speak("Medicine analysis failed. Please try again.")
            }
        }
    }

    private fun triggerVoiceGuidance(status: VerificationStatus) {
        when (status) {
            VerificationStatus.CORRECT_MEDICINE -> {
                voiceHelper.speak("Medicine verified successfully. Marked as taken.")
            }
            VerificationStatus.ALREADY_VERIFIED -> {
                voiceHelper.speak("This dose has already been verified today. Dose was not marked as taken again.")
            }
            VerificationStatus.UNSCHEDULED_TIME -> {
                voiceHelper.speak("Warning. This medicine is in your prescription, but is not scheduled for this time slot. Dose was not marked as taken.")
            }
            VerificationStatus.MEDICINE_NOT_FOUND -> {
                voiceHelper.speak("Warning. This medicine does not match your prescription. Dose was not marked as taken.")
            }
            VerificationStatus.UNABLE_TO_VERIFY, VerificationStatus.UNKNOWN_MEDICINE -> {
                voiceHelper.speak("The medicine could not be reliably identified. Dose was not marked as taken.")
            }
        }
    }

    fun onBarcodeCaptured(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = CameraUiState.Loading
            val barcode = try {
                barcodeHelper.scanBarcode(bitmap)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error scanning barcode", e)
                null
            }
            if (barcode != null) {
                val info = repository.getMedicineByBarcode(barcode)
                if (info != null) {
                    val result = RecognitionResult(
                        name = info.medicineName,
                        confidence = 1.0f,
                        inferenceTime = 0
                    )
                    verifyBarcodeMedicine(result)
                } else {
                    _uiState.value = CameraUiState.Error("Medicine not found for barcode: $barcode")
                }
            } else {
                _uiState.value = CameraUiState.Error("No barcode detected")
            }
        }
    }

    private suspend fun verifyBarcodeMedicine(result: RecognitionResult) {
        val verification = repository.verifyRecognition(
            recognitionResult = result,
            ocrText = null,
            queryBitmap = null,
            targetMedicine = targetMedicine,
            targetSlot = targetSlot,
            targetTime = targetTime,
            prescriptionId = prescriptionId
        )
        _verificationResult.value = verification
        _uiState.value = CameraUiState.Success(result)
        triggerVoiceGuidance(verification.status)
    }

    fun resetState() {
        _uiState.value = CameraUiState.Idle
        _capturedBitmap.value = null
    }

    fun retry() {
        val bitmap = _capturedBitmap.value
        if (bitmap != null) {
            processImage(bitmap)
        } else {
            _uiState.value = CameraUiState.Idle
        }
    }
}

sealed class CameraUiState {
    object Idle : CameraUiState()
    object Loading : CameraUiState()
    data class Success(val result: RecognitionResult) : CameraUiState()
    data class Error(val message: String) : CameraUiState()
}
