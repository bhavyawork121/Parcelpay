package com.parcelpay.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CaptureScreen(
    onNavigateToReview: (String) -> Unit,
    onBack: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewScreen(onNavigateToReview = onNavigateToReview, onBack = onBack)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                "We need camera access to capture parcel labels."
            } else {
                "Camera permission was denied. Please enable it in Settings."
            }
            Text(textToShow, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            if (cameraPermissionState.status.shouldShowRationale) {
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Request Permission")
                }
            } else {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun CameraPreviewScreen(
    onNavigateToReview: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }

    if (capturedPhotoPath != null) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = File(capturedPhotoPath!!),
                contentDescription = "Captured Photo",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { capturedPhotoPath = null }) {
                    Text("Retake")
                }
                Button(onClick = { onNavigateToReview(capturedPhotoPath!!) }) {
                    Text("Use Photo")
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
            
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
            ) {
                Text("Back", color = Color.White)
            }
            
            Button(
                onClick = {
                    val photoFile = File(
                        context.cacheDir,
                        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
                    )
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                try {
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                                    val maxDim = 1600f
                                    val ratio = Math.min(maxDim / bitmap.width, maxDim / bitmap.height)
                                    if (ratio < 1f) {
                                        val newWidth = (bitmap.width * ratio).toInt()
                                        val newHeight = (bitmap.height * ratio).toInt()
                                        val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                                        val out = java.io.FileOutputStream(photoFile)
                                        resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                        out.flush()
                                        out.close()
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraCapture", "Failed to compress image", e)
                                }
                                capturedPhotoPath = photoFile.absolutePath
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("CameraCapture", "Photo capture failed: ${exc.message}", exc)
                            }
                        }
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) { }
        }
    }
}
