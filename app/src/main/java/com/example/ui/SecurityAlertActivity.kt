package com.example.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.BiometricRepository
import com.example.data.SecurityLog
import com.example.speech.VoiceAlertManager
import com.example.ui.theme.Theme_EyeGuard
import com.example.util.DeviceLockManager
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class SecurityAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CANDIDATE_FACE = "extra_candidate_face"
        const val EXTRA_EYE_SCORE = "extra_eye_score"
        const val EXTRA_FACE_SCORE = "extra_face_score"
        const val EXTRA_REASON = "extra_reason"
    }

    private var voiceManager: VoiceAlertManager? = null
    private var lockManager: DeviceLockManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen, stay on top of lock screen
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

        voiceManager = VoiceAlertManager(this)
        lockManager = DeviceLockManager(this)

        // Trigger authoritative warning voice and vibration
        voiceManager?.speakUnknownIdentityAlert()
        lockManager?.triggerWarningVibration()

        val candidateFacePath = intent.getStringExtra(EXTRA_CANDIDATE_FACE)
        val eyeScore = intent.getFloatExtra(EXTRA_EYE_SCORE, 0.25f)
        val faceScore = intent.getFloatExtra(EXTRA_FACE_SCORE, 0.35f)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Unknown identity detected"

        setContent {
            Theme_EyeGuard {
                HeavySecurityAlertScreen(
                    candidateFacePath = candidateFacePath,
                    eyeScore = eyeScore,
                    faceScore = faceScore,
                    reason = reason,
                    onPasswordCorrect = {
                        voiceManager?.speak("Identity verified. System unlocked.")
                        Toast.makeText(this, "Password correct! Access granted.", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onTimeExpired = {
                        voiceManager?.speak("Lockdown initiated. Device locked.")
                        val locked = lockManager?.lockDeviceNow() ?: false
                        if (locked) {
                            finish()
                        }
                    },
                    onRequestDeviceAdmin = {
                        startActivity(lockManager?.getDeviceAdminIntent())
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager?.shutdown()
    }
}

@Composable
fun HeavySecurityAlertScreen(
    candidateFacePath: String?,
    eyeScore: Float,
    faceScore: Float,
    reason: String,
    onPasswordCorrect: () -> Unit,
    onTimeExpired: () -> Unit,
    onRequestDeviceAdmin: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { BiometricRepository.getInstance(context) }
    val owner = remember { repo.getOwnerBiometrics() }
    val lockManager = remember { DeviceLockManager(context) }

    var remainingSeconds by remember { mutableIntStateOf(10) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isLockedState by remember { mutableStateOf(false) }

    // Pulsing animation for heavy alarm aesthetic
    val infiniteTransition = rememberInfiniteTransition(label = "alarm")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // 10-second countdown timer
    LaunchedEffect(isLockedState) {
        if (!isLockedState) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
            }
            // 10 seconds expired!
            isLockedState = true
            repo.addLog(
                SecurityLog(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    status = "SECURITY_LOCKED",
                    eyeScore = eyeScore,
                    faceScore = faceScore,
                    message = "10s timeout expired: Device locked automatically",
                    snapshotPath = candidateFacePath
                )
            )
            onTimeExpired()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF450A0A).copy(alpha = pulseAlpha),
                        Color(0xFF180505),
                        Color(0xFF0F0202)
                    )
                )
            )
            .testTag("heavy_security_alert_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Hazard Banner
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color(0xFFDC2626).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert Hazard",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SECURITY PROTOCOL ACTIVE",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Text(
                    text = "UNKNOWN IDENTITY\nDETECTED",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Eyes & Face do not match authorized owner",
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Candidate Snapshot & Biometric Scores Card
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo preview
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                            .background(Color(0xFF261010)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!candidateFacePath.isNullOrEmpty() && File(candidateFacePath).exists()) {
                            AsyncImage(
                                model = File(candidateFacePath),
                                contentDescription = "Detected Face Snapshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BIOMETRIC CHECKS",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Check 1 (Eyes): ${(eyeScore * 100).toInt()}% match (FAILED)",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Check 2 (Face): ${(faceScore * 100).toInt()}% match (FAILED)",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Reason: $reason",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 10s Timer Graphic & Prompt
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { remainingSeconds / 10f },
                        modifier = Modifier.fillMaxSize(),
                        color = if (remainingSeconds <= 3) Color(0xFFEF4444) else Color(0xFFF59E0B),
                        strokeWidth = 7.dp,
                        trackColor = Color(0xFF374151)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$remainingSeconds",
                            color = if (remainingSeconds <= 3) Color(0xFFEF4444) else Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SECONDS",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User requested prompt: "whar is security password"
                Text(
                    text = "WHAT IS SECURITY PASSWORD?",
                    color = Color(0xFFFDE047),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Enter password within $remainingSeconds seconds or mobile will lock immediately",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field and Action
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        passwordError = null
                    },
                    label = { Text("Security Password") },
                    placeholder = { Text("Enter password to unlock") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (passwordInput.trim() == owner.securityPassword.trim()) {
                            onPasswordCorrect()
                        } else {
                            passwordError = "Incorrect password! Try again."
                        }
                    }),
                    isError = passwordError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFDE047),
                        unfocusedBorderColor = Color(0xFF7F1D1D),
                        errorBorderColor = Color(0xFFEF4444),
                        focusedLabelColor = Color(0xFFFDE047),
                        unfocusedLabelColor = Color.Gray,
                        focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Password",
                            tint = Color(0xFFFDE047)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("security_password_input")
                )

                if (passwordError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = passwordError ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (passwordInput.trim() == owner.securityPassword.trim()) {
                            onPasswordCorrect()
                        } else {
                            passwordError = "Incorrect password! Access denied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_password_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VERIFY & UNLOCK MOBILE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }

                if (!lockManager.isDeviceAdminActive()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestDeviceAdmin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enable_admin_alert_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Device Admin (for LockNow)",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
