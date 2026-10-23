package com.smartmedguardian.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel


/**
 * Helper class for TensorFlow Lite operations.
 */
class TensorFlowHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val modelPath = "medicine_model.tflite"
    private val labelsPath = "labels.json"
    private val labels = mutableListOf<String>()

    init {
        loadLabels()
        initInterpreter()
    }

    private fun loadLabels() {
        try {
            val jsonString = context.assets.open(labelsPath).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            labels.clear()
            for (i in 0 until jsonArray.length()) {
                labels.add(jsonArray.getString(i))
            }
            Log.d("TensorFlowHelper", "Loaded ${labels.size} labels from $labelsPath: $labels")
        } catch (e: Exception) {
            Log.e("TensorFlowHelper", "Error loading labels from $labelsPath: ${e.message}")
        }
    }

    private fun initInterpreter() {
        try {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()
            
            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
                Log.d("TensorFlowHelper", "GPU Delegate supported and added")
            } else {
                options.setNumThreads(4)
                Log.d("TensorFlowHelper", "GPU Delegate not supported, using CPU with 4 threads")
            }

            val modelBuffer = loadModelFile(context.assets, modelPath)
            val interp = Interpreter(modelBuffer, options)
            interpreter = interp

            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)
            
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()

            Log.d("TensorFlowHelper", "Model loaded successfully: $modelPath")
            Log.d("TensorFlowHelper", "Model Input Shape: ${inputShape.contentToString()}, DataType: ${inputTensor.dataType()}")
            Log.d("TensorFlowHelper", "Model Output Shape: ${outputShape.contentToString()}, DataType: ${outputTensor.dataType()}")
            Log.d("TensorFlowHelper", "Loaded labels count: ${labels.size}, Model output count: ${if (outputShape.size > 1) outputShape[1] else 0}")
            
            if (outputShape.size > 1 && outputShape[1] != labels.size) {
                Log.w("TensorFlowHelper", "Warning: Label count (${labels.size}) does not match model output size (${outputShape[1]})")
            }
        } catch (e: Exception) {
            Log.e("TensorFlowHelper", "Error initializing interpreter with GPU delegate: ${e.message}. Falling back to CPU...")
            tryCPUFallback()
        }
    }

    private fun tryCPUFallback() {
        try {
            gpuDelegate?.close()
            gpuDelegate = null
            
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val modelBuffer = loadModelFile(context.assets, modelPath)
            val interp = Interpreter(modelBuffer, options)
            interpreter = interp

            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)

            Log.d("TensorFlowHelper", "CPU Fallback loaded successfully")
            Log.d("TensorFlowHelper", "Model Input Shape: ${inputTensor.shape().contentToString()}, DataType: ${inputTensor.dataType()}")
            Log.d("TensorFlowHelper", "Model Output Shape: ${outputTensor.shape().contentToString()}, DataType: ${outputTensor.dataType()}")
        } catch (e: Exception) {
            Log.e("TensorFlowHelper", "Error in CPU fallback: ${e.message}")
            interpreter = null
        }
    }

    fun isModelLoaded(): Boolean = interpreter != null

    /**
     * Runs inference on the provided bitmap.
     */
    fun classifyImage(bitmap: Bitmap): RecognitionResult {
        val interp = interpreter ?: throw IllegalStateException("Interpreter is not initialized")

        val startTime = SystemClock.uptimeMillis()

        // 1. Preprocess the image
        val inputDataType = interp.getInputTensor(0).dataType()
        val tensorImage = TensorImage(inputDataType)
        tensorImage.load(bitmap)

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        val processedImage = imageProcessor.process(tensorImage)

        // 2. Prepare dynamic output buffer based on model output size
        val outputShape = interp.getOutputTensor(0).shape()
        val numClasses = if (outputShape.size > 1) outputShape[1] else labels.size
        val outputBuffer = Array(1) { FloatArray(numClasses) }

        // 3. Run inference
        interp.run(processedImage.buffer, outputBuffer)

        val endTime = SystemClock.uptimeMillis()
        val inferenceTime = endTime - startTime

        // 4. Post-process top-1 and top-2 probabilities
        val results = outputBuffer[0]
        var maxIdx = 0
        var maxConf = -Float.MAX_VALUE
        var secondMaxConf = -Float.MAX_VALUE
        
        for (i in results.indices) {
            if (results[i] > maxConf) {
                secondMaxConf = maxConf
                maxConf = results[i]
                maxIdx = i
            } else if (results[i] > secondMaxConf) {
                secondMaxConf = results[i]
            }
        }

        val margin = if (secondMaxConf > -Float.MAX_VALUE) (maxConf - secondMaxConf) else maxConf
        val isKnownEnough = maxConf >= 0.70f && margin >= 0.20f

        val medicineName = if (maxIdx in labels.indices) {
            labels[maxIdx]
        } else {
            "Unknown Class $maxIdx"
        }

        Log.d("TensorFlowHelper", "Classification predicted $medicineName ($maxConf) in ${inferenceTime}ms")

        return RecognitionResult(
            name = medicineName,
            confidence = maxConf,
            inferenceTime = inferenceTime,
            top2Margin = margin,
            isKnownEnough = isKnownEnough
        )
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}
