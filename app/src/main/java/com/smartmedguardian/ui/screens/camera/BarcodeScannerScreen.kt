package com.smartmedguardian.ui.screens.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.viewmodel.CameraViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import androidx.core.content.ContextCompat

@Composable
fun BarcodeScannerScreen(
    viewModel: CameraViewModel,
    onBackClick: () -> Unit,
    onResultReady: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
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

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("BarcodeScanner", "Use case binding failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeumorphicIconButton(
                        onClick = onBackClick,
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextDark) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Scan Barcode", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            },
            bottomBar = {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                    NeumorphicButton(
                        text = "Scan Now",
                        onClick = {
                            imageCapture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bitmap = image.toBitmap()
                                        image.close()
                                        coroutineScope.launch {
                                            viewModel.onBarcodeCaptured(bitmap)
                                            onResultReady()
                                        }
                                    }
                                }
                            )
                        },
                        color = MedicalGreen,
                        textColor = Color.White
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}
