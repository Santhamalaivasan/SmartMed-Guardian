package com.smartmedguardian.util

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

class BarcodeScannerHelper {
    private val scanner = BarcodeScanning.getClient()

    suspend fun scanBarcode(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = scanner.process(image).await()
            result.firstOrNull()?.rawValue
        } catch (e: Exception) {
            null
        }
    }
}
