package com.smartmedguardian.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Helper class for extracting 1280-dimensional visual feature embeddings
 * from medicine images using the medicine_embedding.tflite model.
 */
class MedicineEmbeddingHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val modelPath = "medicine_embedding.tflite"

    init {
        initInterpreter()
    }

    private fun initInterpreter() {
        try {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()

            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
                Log.d("MedicineEmbeddingHelper", "GPU Delegate supported and added for embedding model")
            } else {
                options.setNumThreads(4)
                Log.d("MedicineEmbeddingHelper", "Using CPU with 4 threads for embedding model")
            }

            val modelBuffer = loadModelFile(context.assets, modelPath)
            interpreter = Interpreter(modelBuffer, options)
            Log.d("MedicineEmbeddingHelper", "Embedding model loaded successfully from $modelPath")
        } catch (e: Exception) {
            Log.e("MedicineEmbeddingHelper", "Failed to init GPU delegate for embedding model: ${e.message}. Fallback to CPU...")
            tryCPUFallback()
        }
    }

    private fun tryCPUFallback() {
        try {
            gpuDelegate?.close()
            gpuDelegate = null
            val options = Interpreter.Options().apply { setNumThreads(4) }
            val modelBuffer = loadModelFile(context.assets, modelPath)
            interpreter = Interpreter(modelBuffer, options)
            Log.d("MedicineEmbeddingHelper", "CPU fallback successful for embedding model")
        } catch (e: Exception) {
            Log.e("MedicineEmbeddingHelper", "Error in CPU fallback for embedding model: ${e.message}", e)
            interpreter = null
        }
    }

    fun isModelLoaded(): Boolean = interpreter != null

    /**
     * Extracts a normalized 1280-dim embedding vector from a Bitmap image.
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val interp = interpreter ?: throw IllegalStateException("Embedding interpreter is not initialized")

        val inputTensor = interp.getInputTensor(0)
        val tensorImage = TensorImage(inputTensor.dataType())
        tensorImage.load(bitmap)

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        val processedImage = imageProcessor.process(tensorImage)

        val outputBuffer = Array(1) { FloatArray(1280) }
        interp.run(processedImage.buffer, outputBuffer)

        val rawEmbedding = outputBuffer[0]
        return normalizeL2(rawEmbedding)
    }

    /**
     * Helper to load bitmap from file path and extract normalized embedding vector.
     */
    fun extractEmbeddingFromFile(filePath: String): FloatArray? {
        return try {
            val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
            val embedding = extractEmbedding(bitmap)
            bitmap.recycle()
            embedding
        } catch (e: Exception) {
            Log.e("MedicineEmbeddingHelper", "Error extracting embedding from file: $filePath", e)
            null
        }
    }

    /**
     * Normalizes a float vector to unit L2 length.
     */
    private fun normalizeL2(vector: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vector) {
            sumSq += (v * v)
        }
        val norm = sqrt(sumSq).toFloat()
        if (norm == 0f) return vector

        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / norm
        }
        return normalized
    }

    private fun loadModelFile(assetManager: AssetManager, path: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(path)
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

    companion object {
        fun floatArrayToString(array: FloatArray): String = array.joinToString(",")
        fun stringToFloatArray(str: String): FloatArray = str.split(",").map { it.trim().toFloat() }.toFloatArray()
    }
}
