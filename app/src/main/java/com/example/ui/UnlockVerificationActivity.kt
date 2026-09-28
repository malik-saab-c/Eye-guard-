package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.biometrics.BiometricMatchResult
import com.example.biometrics.BiometricMatcher
import com.example.biometrics.ExtractedBiometrics
import com.example.data.BiometricRepository
import com.example.data.SecurityLog
import com.example.ui.theme.Theme_EyeGuard
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.Executors

class UnlockVerificationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRIGGER_TYPE = "extra_trigger_type"
        const val EXTRA_SIMULATE_INTRUDER = "extra_simulate_intruder"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isVerificationEvaluated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show when locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE) ?: "MANUAL_TEST"
        val simulateIntruder = intent.getBooleanExtra(EXTRA_SIMULATE_INTRUDER, false)

        setContent {
            Theme_EyeGuard {
                UnlockScannerScreen(
                    triggerType = triggerType,
                    simulateIntruder = simulateIntruder,
                    onVerifiedOwner = {
                        finish()
                    },
                    onUnknownIdentity = { result, candidateFacePath ->
                        val alertIntent = Intent(this@UnlockVerificationActivity, SecurityAlertActivity::class.java).apply {
                            putExtra(SecurityAlertActivity.EXTRA_CANDIDATE_FACE, candidateFacePath)
                            putExtra(SecurityAlertActivity.EXTRA_EYE_SCORE, result.eyeScore)
                            putExtra(SecurityAlertActivity.EXTRA_FACE_SCORE, result.faceScore)
                            putExtra(SecurityAlertActivity.EXTRA_REASON, result.reason)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(alertIntent)
                        finish()
                    },
                    onDismiss = {
                        finish()
                    },
                    onBindCamera = { previewView, onFaceDetected ->
                        startCamera(previewView, simulateIntruder, onFaceDetected)
                    }
                )
            }
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun startCamera(
        previewView: PreviewView,
        forceMismatch: Boolean,
        onBiometricsExtracted: (ExtractedBiometrics, Bitmap) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.2f)
                    .build()
                val detector = FaceDetection.getClient(options)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (isVerificationEvaluated) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                if (isVerificationEvaluated) {
                                    imageProxy.close()
                                    return@addOnSuccessListener
                                }

                                if (faces.isNotEmpty()) {
                                    val face = faces[0]
                                    val bitmap = imageProxyToBitmap(imageProxy, rotationDegrees)
                                    val biometrics = BiometricMatcher.extractBiometrics(face, bitmap)

                                    if (biometrics != null && biometrics.isEyesClear) {
                                        // EYES ARE CLEARLY DETECTED!
                                        // Immediately turn off camera as specified:
                                        isVerificationEvaluated = true
                                        runOnUiThread {
                                            shutdownCamera()
                                            onBiometricsExtracted(biometrics, bitmap)
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener {
                                // ignore frame errors
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

            } catch (e: Exception) {
                Log.e("UnlockVerification", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun shutdownCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
        } catch (e: Exception) {
            Log.e("UnlockVerification", "Error unbinding camera", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownCamera()
        cameraExecutor.shutdown()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap {
        val nv21 = yuv420ToNv21(imageProxy)
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
        val imageBytes = out.toByteArray()
        val original = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            // Front camera mirror
            postScale(-1f, 1f, original.width / 2f, original.height / 2f)
        }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
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
}

@Composable
fun UnlockScannerScreen(
    triggerType: String,
    simulateIntruder: Boolean,
    onVerifiedOwner: () -> Unit,
    onUnknownIdentity: (BiometricMatchResult, String?) -> Unit,
    onDismiss: () -> Unit,
    onBindCamera: (PreviewView, (ExtractedBiometrics, Bitmap) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { BiometricRepository.getInstance(context) }
    val owner = remember { repo.getOwnerBiometrics() }

    var statusMessage by remember { mutableStateOf("Initializing Front Camera...") }
    var scanSubtext by remember { mutableStateOf("Position eyes inside the cyber scanner reticle") }
    var verificationStatus by remember { mutableStateOf<BiometricMatchResult?>(null) }
    var cameraTurnedOff by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    // Timeout fallback (8 seconds)
    LaunchedEffect(Unit) {
        delay(8000)
        if (verificationStatus == null) {
            statusMessage = "Verification timeout. Face not clear."
            delay(1000)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .testTag("unlock_scanner_container")
    ) {
        // Camera Preview
        if (!cameraTurnedOff) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        onBindCamera(this) { biometrics, bitmap ->
                            cameraTurnedOff = true
                            statusMessage = "Eyes Detected! Camera Off. Analyzing..."
                            scanSubtext = "Executing Check 1 (Eyes) & Check 2 (Face)..."

                            // Load owner eye bitmap
                            val ownerEyeBitmap = repo.loadBitmap(owner.eyeImagePath)
                            var result = BiometricMatcher.verifyIdentity(
                                biometrics,
                                owner,
                                biometrics.eyeCropBitmap,
                                ownerEyeBitmap
                            )

                            if (simulateIntruder) {
                                // Forced test intruder mode
                                result = result.copy(
                                    isVerified = false,
                                    eyeScore = 0.28f,
                                    faceScore = 0.35f,
                                    eyeCheckPassed = false,
                                    faceCheckPassed = false,
                                    reason = "Simulated Intruder Eye/Face Mismatch"
                                )
                            }

                            verificationStatus = result

                            // Save snapshot
                            val candidatePath = repo.saveBitmap(
                                biometrics.faceCropBitmap ?: bitmap,
                                "candidate_${System.currentTimeMillis()}.jpg"
                            )

                            // Log result
                            repo.addLog(
                                SecurityLog(
                                    id = UUID.randomUUID().toString(),
                                    timestamp = System.currentTimeMillis(),
                                    status = if (result.isVerified) "AUTHORIZED_OWNER" else "UNKNOWN_IDENTITY_ALERT",
                                    eyeScore = result.eyeScore,
                                    faceScore = result.faceScore,
                                    message = result.reason,
                                    snapshotPath = candidatePath
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview_view")
            )
        } else {
            // Camera Turned Off Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF060D1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Camera Disengaged",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "CAMERA DISENGAGED",
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Images secured in encrypted RAM",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Cyber Scanner HUD Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            val reticleWidth = width * 0.78f
            val reticleHeight = height * 0.42f

            // Dark semi-transparent vignetting around reticle
            drawRect(
                color = Color.Black.copy(alpha = 0.65f),
                size = size
            )

            // Scanning line
            val currentLineY = (centerY - reticleHeight / 2f) + (reticleHeight * scanLineY)
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFF00E5FF), Color.Transparent)
                ),
                start = Offset(centerX - reticleWidth / 2f, currentLineY),
                end = Offset(centerX + reticleWidth / 2f, currentLineY),
                strokeWidth = 4.dp.toPx()
            )
        }

        // Top Status Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (cameraTurnedOff) Color(0xFFF59E0B) else Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (cameraTurnedOff) "CAMERA OFF" else "ACTIVE SENSING",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp).testTag("dismiss_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusMessage,
                color = Color(0xFF38BDF8),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scanSubtext,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        // Center Eye Reticle Guide
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 280.dp, height = 180.dp)
                .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Left Eye Target
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("L", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Right Eye Target
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("R", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Result Card or Processing
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            verificationStatus?.let { result ->
                LaunchedEffect(result) {
                    if (result.isVerified) {
                        Toast.makeText(context, "Welcome Owner! Identity Verified.", Toast.LENGTH_SHORT).show()
                        delay(800)
                        onVerifiedOwner()
                    } else {
                        // Unknown Identity! Launch heavy Security Alert
                        delay(500)
                        val lastLog = repo.getLogs().firstOrNull()
                        onUnknownIdentity(result, lastLog?.snapshotPath)
                    }
                }

                Surface(
                    color = if (result.isVerified) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (result.isVerified) Color(0xFF34D399) else Color(0xFFEF4444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (result.isVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Result Status",
                                tint = if (result.isVerified) Color(0xFF34D399) else Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (result.isVerified) "OWNER VERIFIED" else "UNKNOWN IDENTITY DETECTED",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check 1 (Eyes): ${(result.eyeScore * 100).toInt()}% • Check 2 (Face): ${(result.faceScore * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = result.reason,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
