package com.example.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.biometrics.BiometricMatcher
import com.example.biometrics.ExtractedBiometrics
import com.example.data.BiometricRepository
import com.example.data.OwnerBiometrics
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun OwnerRegistrationScreen(
    onRegistrationComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember { BiometricRepository.getInstance(context) }

    var latestBiometrics by remember { mutableStateOf<ExtractedBiometrics?>(null) }
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var feedbackText by remember { mutableStateOf("Position your eyes in the frame") }
    var eyesDetected by remember { mutableStateOf(false) }

    var capturedBiometrics by remember { mutableStateOf<ExtractedBiometrics?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var passwordInput by remember { mutableStateOf("1234") }
    var isSaving by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .testTag("owner_registration_container")
    ) {
        if (capturedBiometrics == null) {
            // Live Camera Capture Mode
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }

                            val options = FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                .build()
                            val detector = FaceDetection.getClient(options)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val image = InputImage.fromMediaImage(mediaImage, rotation)

                                    detector.process(image)
                                        .addOnSuccessListener { faces ->
                                            if (faces.isNotEmpty()) {
                                                val face = faces[0]
                                                val bmp = imageProxyToBmp(imageProxy, rotation)
                                                val bio = BiometricMatcher.extractBiometrics(face, bmp)

                                                if (bio != null) {
                                                    latestBiometrics = bio
                                                    latestBitmap = bmp
                                                    eyesDetected = bio.isEyesClear
                                                    feedbackText = if (bio.isEyesClear) {
                                                        "Eyes Detected & Clear! Tap Capture"
                                                    } else {
                                                        "Open eyes clearly and look straight"
                                                    }
                                                }
                                            } else {
                                                eyesDetected = false
                                                feedbackText = "No face detected in frame"
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
                ) {
                    Text(
                        text = "OWNER REGISTRATION",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Center Reticle
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 280.dp, height = 180.dp)
                    .border(
                        2.dp,
                        if (eyesDetected) Color(0xFF34D399) else Color(0xFF38BDF8),
                        RoundedCornerShape(16.dp)
                    )
            )

            // Bottom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (eyesDetected) Icons.Default.CheckCircle else Icons.Default.Visibility,
                            contentDescription = "Status",
                            tint = if (eyesDetected) Color(0xFF34D399) else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = feedbackText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = {
                        if (latestBiometrics != null && eyesDetected) {
                            capturedBiometrics = latestBiometrics
                            capturedBitmap = latestBitmap
                        } else {
                            Toast.makeText(context, "Please align your eyes inside the scanner", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (eyesDetected) Color(0xFF0284C7) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("capture_owner_eyes_button")
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Capture")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CAPTURE EYES & FACE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else {
            // Confirmation & Security Password Setup Step
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Eyes Biometrics Extracted!",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Check 1 (Eyes) & Check 2 (Face) geometry calibrated",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Security Password input (for the prompt: "whar is security password")
                        Text(
                            text = "SET SECURITY PASSWORD",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Backup Security Password") },
                            placeholder = { Text("e.g. 1234") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Key, contentDescription = "Password")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("set_security_password_input")
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (passwordInput.isBlank()) {
                                    Toast.makeText(context, "Please enter a security password", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSaving = true
                                val bio = capturedBiometrics ?: return@Button
                                val bmp = capturedBitmap ?: return@Button

                                val eyePath = bio.eyeCropBitmap?.let {
                                    repo.saveBitmap(it, "owner_eye_reference.jpg")
                                }
                                val facePath = bio.faceCropBitmap?.let {
                                    repo.saveBitmap(it, "owner_face_reference.jpg")
                                } ?: repo.saveBitmap(bmp, "owner_face_reference.jpg")

                                val owner = OwnerBiometrics(
                                    isRegistered = true,
                                    isActive = true, // activate automatically on capture as requested!
                                    securityPassword = passwordInput.trim(),
                                    eyeDistanceRatio = bio.eyeDistanceRatio,
                                    leftEyeToNoseRatio = bio.leftEyeToNoseRatio,
                                    rightEyeToNoseRatio = bio.rightEyeToNoseRatio,
                                    mouthToEyesRatio = bio.mouthToEyesRatio,
                                    eyeAspectRatio = bio.eyeAspectRatio,
                                    faceAspectRatio = bio.faceAspectRatio,
                                    eyeImagePath = eyePath,
                                    faceImagePath = facePath,
                                    registeredTimestamp = System.currentTimeMillis()
                                )

                                repo.saveOwnerBiometrics(owner)
                                Toast.makeText(context, "EyeGuard Activated Successfully!", Toast.LENGTH_LONG).show()
                                onRegistrationComplete()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("activate_protection_button")
                        ) {
                            Text(
                                text = "ACTIVATE EYEGUARD PROTECTION",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToBmp(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap {
    val nv21 = yuv420ToNv21Bytes(imageProxy)
    val yuvImage = android.graphics.YuvImage(
        nv21,
        android.graphics.ImageFormat.NV21,
        imageProxy.width,
        imageProxy.height,
        null
    )
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
        90,
        out
    )
    val bytes = out.toByteArray()
    val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
        postScale(-1f, 1f, original.width / 2f, original.height / 2f)
    }
    return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
}

private fun yuv420ToNv21Bytes(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    return nv21
}
