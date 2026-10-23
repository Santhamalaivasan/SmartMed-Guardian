package com.smartmedguardian.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

class TextRecognitionHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(originalBitmap: Bitmap): TextResult {
        val ocrStart = System.currentTimeMillis()
        val collectedTextLines = LinkedHashSet<String>()

        Log.d("TextRecognitionHelper", "STARTING OPTIMIZED OCR PIPELINE (Input: ${originalBitmap.width}x${originalBitmap.height})")

        // Step 1: Downscale to max dimension 1080px for optimal speed and OCR readability
        val scaledBitmap = scaleBitmapToMaxDimension(originalBitmap, maxDimension = 1080)
        val needRecycleScaled = (scaledBitmap !== originalBitmap)

        var maxCharCountDetected = 0
        var bestAngle = 0

        try {
            // Attempt 1: Standard 0° orientation (fast path)
            val image0 = InputImage.fromBitmap(scaledBitmap, 0)
            val result0 = recognizer.process(image0).await()
            val text0 = result0.text

            if (text0.isNotBlank()) {
                text0.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.length >= 2) {
                        collectedTextLines.add(trimmed)
                    }
                }
                maxCharCountDetected = text0.length
            }

            // Secondary attempts ONLY if 0° yielded insufficient text (< 6 chars)
            if (maxCharCountDetected < 6) {
                // Attempt 2: 180° rotation
                var rotated180: Bitmap? = null
                try {
                    rotated180 = rotateBitmap(scaledBitmap, 180f)
                    val image180 = InputImage.fromBitmap(rotated180, 0)
                    val result180 = recognizer.process(image180).await()
                    val text180 = result180.text
                    if (text180.isNotBlank()) {
                        text180.lines().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.length >= 2) {
                                collectedTextLines.add(trimmed)
                            }
                        }
                        if (text180.length > maxCharCountDetected) {
                            maxCharCountDetected = text180.length
                            bestAngle = 180
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TextRecognitionHelper", "Error in 180° OCR attempt: ${e.message}")
                } finally {
                    if (rotated180 != null && rotated180 !== scaledBitmap && rotated180 !== originalBitmap) {
                        if (!rotated180.isRecycled) {
                            rotated180.recycle()
                        }
                    }
                }

                // Attempt 3: High-contrast variant ONLY if still < 6 chars
                if (maxCharCountDetected < 6) {
                    var hcBitmap: Bitmap? = null
                    try {
                        hcBitmap = createHighContrastBitmap(scaledBitmap)
                        if (hcBitmap != null) {
                            val imageHc = InputImage.fromBitmap(hcBitmap, 0)
                            val resultHc = recognizer.process(imageHc).await()
                            val textHc = resultHc.text
                            if (textHc.isNotBlank()) {
                                textHc.lines().forEach { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.length >= 2) {
                                        collectedTextLines.add(trimmed)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TextRecognitionHelper", "Error in high-contrast OCR attempt: ${e.message}")
                    } finally {
                        if (hcBitmap != null && hcBitmap !== scaledBitmap && hcBitmap !== originalBitmap) {
                            if (!hcBitmap.isRecycled) {
                                hcBitmap.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            if (needRecycleScaled && scaledBitmap !== originalBitmap) {
                if (!scaledBitmap.isRecycled) {
                    scaledBitmap.recycle()
                }
            }
        }

        val combinedRawText = collectedTextLines.joinToString("\n")
        val expiryDate = extractExpiryDate(combinedRawText)
        val batchNumber = extractBatchNumber(combinedRawText)

        val ocrElapsed = System.currentTimeMillis() - ocrStart
        Log.d("TextRecognitionHelper", "OCR completed in ${ocrElapsed}ms (Chars: ${combinedRawText.length}, Best Angle: $bestAngle°)")

        return TextResult(expiryDate, batchNumber, combinedRawText)
    }



    private fun scaleBitmapToMaxDimension(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val maxCurrent = maxOf(width, height)
        if (maxCurrent <= maxDimension) return source

        val scale = maxDimension.toFloat() / maxCurrent
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun centerCropBitmap(source: Bitmap, cropFactor: Float): Bitmap {
        val width = source.width
        val height = source.height
        val cropW = (width * cropFactor).toInt()
        val cropH = (height * cropFactor).toInt()
        val startX = (width - cropW) / 2
        val startY = (height - cropH) / 2

        return Bitmap.createBitmap(source, startX, startY, cropW, cropH)
    }

    private fun createGrayscaleBitmap(source: Bitmap): Bitmap? {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            Log.e("TextRecognitionHelper", "Error creating grayscale bitmap: ${e.message}")
            null
        }
    }

    private fun createHighContrastBitmap(source: Bitmap): Bitmap? {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()

            // Contrast factor 1.8f
            val contrast = 1.8f
            val translate = 128f * (1f - contrast)
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            val grayMatrix = ColorMatrix()
            grayMatrix.setSaturation(0f)
            grayMatrix.postConcat(contrastMatrix)

            paint.colorFilter = ColorMatrixColorFilter(grayMatrix)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            Log.e("TextRecognitionHelper", "Error creating high-contrast bitmap: ${e.message}")
            null
        }
    }

    private fun createSharpenedBitmap(source: Bitmap): Bitmap? {
        return try {
            val width = source.width
            val height = source.height
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            val outputPixels = IntArray(width * height)

            System.arraycopy(pixels, 0, outputPixels, 0, width)
            System.arraycopy(pixels, (height - 1) * width, outputPixels, (height - 1) * width, width)

            for (y in 1 until height - 1) {
                val yWidth = y * width
                val topWidth = (y - 1) * width
                val bottomWidth = (y + 1) * width

                outputPixels[yWidth] = pixels[yWidth]
                outputPixels[yWidth + width - 1] = pixels[yWidth + width - 1]

                for (x in 1 until width - 1) {
                    val idx = yWidth + x
                    val center = pixels[idx]
                    val top = pixels[topWidth + x]
                    val bottom = pixels[bottomWidth + x]
                    val left = pixels[yWidth + x - 1]
                    val right = pixels[yWidth + x + 1]

                    val rC = (center shr 16) and 0xFF
                    val gC = (center shr 8) and 0xFF
                    val bC = center and 0xFF

                    val rT = (top shr 16) and 0xFF
                    val gT = (top shr 8) and 0xFF
                    val bT = top and 0xFF

                    val rB = (bottom shr 16) and 0xFF
                    val gB = (bottom shr 8) and 0xFF
                    val bB = bottom and 0xFF

                    val rL = (left shr 16) and 0xFF
                    val gL = (left shr 8) and 0xFF
                    val bL = left and 0xFF

                    val rR = (right shr 16) and 0xFF
                    val gR = (right shr 8) and 0xFF
                    val bR = right and 0xFF

                    var r = 5 * rC - rT - rB - rL - rR
                    var g = 5 * gC - gT - gB - gL - gR
                    var b = 5 * bC - bT - bB - bL - bR

                    if (r < 0) r = 0 else if (r > 255) r = 255
                    if (g < 0) g = 0 else if (g > 255) g = 255
                    if (b < 0) b = 0 else if (b > 255) b = 255

                    outputPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.setPixels(outputPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            Log.e("TextRecognitionHelper", "Error creating sharpened bitmap: ${e.message}")
            null
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun normalizeTextForLogging(text: String): String {
        if (text.isBlank()) return ""
        return text
            .lowercase()
            .replace("[^a-z0-9\\s/-]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun extractExpiryDate(text: String): String? {
        val expiryPatterns = listOf(
            Pattern.compile("(?i)(EXP|ED|Expires|Expiry)[:\\s]+(\\d{2}[/-]\\d{2}[/-]\\d{4})"),
            Pattern.compile("(?i)(EXP|ED|Expires|Expiry)[:\\s]+(\\d{2}[/-]\\d{4})"),
            Pattern.compile("(?i)(EXP|ED|Expires|Expiry)[:\\s]+(\\d{2}[/-]\\d{2})")
        )
        
        for (pattern in expiryPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(2)
            }
        }
        return null
    }

    private fun extractBatchNumber(text: String): String? {
        val batchPatterns = listOf(
            Pattern.compile("(?i)(BATCH|BN|LOT|B\\.N\\.)[:\\s]+([A-Z0-9]+)")
        )
        
        for (pattern in batchPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(2)
            }
        }
        return null
    }
}

data class TextResult(
    val expiryDate: String?,
    val batchNumber: String?,
    val rawText: String
)
