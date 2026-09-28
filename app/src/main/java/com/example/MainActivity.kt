package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.BiometricRepository
import com.example.data.OwnerBiometrics
import com.example.data.SecurityLog
import com.example.service.EyeGuardForegroundService
import com.example.ui.OwnerRegistrationScreen
import com.example.ui.SecurityAlertActivity
import com.example.ui.UnlockVerificationActivity
import com.example.ui.theme.Theme_EyeGuard
import com.example.util.DeviceLockManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Theme_EyeGuard {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val repo = remember { BiometricRepository.getInstance(context) }
    val lockManager = remember { DeviceLockManager(context) }

    var ownerBiometrics by remember { mutableStateOf(repo.getOwnerBiometrics()) }
    var securityLogs by remember { mutableStateOf(repo.getLogs()) }
    var isRegistering by remember { mutableStateOf(false) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission is required for EyeGuard", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional on 13+ */ }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refreshState() {
        ownerBiometrics = repo.getOwnerBiometrics()
        securityLogs = repo.getLogs()
    }

    if (isRegistering) {
        OwnerRegistrationScreen(
            onRegistrationComplete = {
                isRegistering = false
                refreshState()
                if (ownerBiometrics.isActive) {
                    EyeGuardForegroundService.startService(context)
                }
            },
            onCancel = {
                isRegistering = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveRedEye,
                                contentDescription = "Logo",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "EYEGUARD",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Biometric Unlock Sentinel",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshState() },
                        modifier = Modifier.testTag("refresh_dashboard_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B1329)
                )
            )
        },
        containerColor = Color(0xFF030712)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Activation Status Hero Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ownerBiometrics.isActive) Color(0xFF064E3B) else Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (ownerBiometrics.isActive) Color(0xFF34D399) else Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("activation_status_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (ownerBiometrics.isActive) Icons.Default.Shield else Icons.Default.Warning,
                                    contentDescription = "Shield",
                                    tint = if (ownerBiometrics.isActive) Color(0xFF34D399) else Color(0xFFF59E0B),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (ownerBiometrics.isActive) "SHIELD ACTIVE" else "SHIELD INACTIVE",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = if (ownerBiometrics.isActive) "Protecting device on unlock" else "Tap below to calibrate eyes",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (ownerBiometrics.isRegistered) {
                                Switch(
                                    checked = ownerBiometrics.isActive,
                                    onCheckedChange = { active ->
                                        repo.setGuardActive(active)
                                        refreshState()
                                        if (active) {
                                            EyeGuardForegroundService.startService(context)
                                        } else {
                                            EyeGuardForegroundService.stopService(context)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.testTag("shield_active_switch")
                                )
                            }
                        }

                        if (!ownerBiometrics.isRegistered) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (!hasCameraPermission) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        isRegistering = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("start_registration_button")
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CAPTURE OWNER EYES TO ACTIVATE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Registered Owner Eyes & Face Profile Card
            if (ownerBiometrics.isRegistered) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "REGISTERED OWNER BIOMETRICS",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                TextButton(
                                    onClick = { isRegistering = true },
                                    modifier = Modifier.testTag("recalibrate_eyes_button")
                                ) {
                                    Text("Re-calibrate", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.5.dp, Color(0xFF0284C7), RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val facePath = ownerBiometrics.faceImagePath ?: ownerBiometrics.eyeImagePath
                                    if (!facePath.isNullOrEmpty() && File(facePath).exists()) {
                                        AsyncImage(
                                            model = File(facePath),
                                            contentDescription = "Registered Owner Face",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Biometric",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "Check 1: Eyes Geometry Calibrated",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Eye Distance Ratio: ${"%.3f".format(ownerBiometrics.eyeDistanceRatio)}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Check 2: Face Symmetry Calibrated",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Triangle Ratio: ${"%.3f".format(ownerBiometrics.mouthToEyesRatio)}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Security Password & Device Policy Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SECURITY PROTOCOLS",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Security Password",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Required if eyes mismatch (10s timer)",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = {
                                    newPasswordInput = ownerBiometrics.securityPassword
                                    showPasswordDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.testTag("change_password_button")
                            ) {
                                Icon(imageVector = Icons.Default.Key, contentDescription = "Key", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Device Admin for LockNow
                        val isAdminActive = lockManager.isDeviceAdminActive()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device Admin (Auto-Lock)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isAdminActive) "Granted: lockNow() active" else "Needed to lock device if 10s timer expires",
                                    color = if (isAdminActive) Color(0xFF34D399) else Color(0xFFF59E0B),
                                    fontSize = 11.sp
                                )
                            }

                            if (!isAdminActive) {
                                Button(
                                    onClick = {
                                        context.startActivity(lockManager.getDeviceAdminIntent())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier.testTag("enable_device_admin_button")
                                ) {
                                    Text("Grant", fontSize = 12.sp)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = Color(0xFF34D399)
                                )
                            }
                        }

                        // Display Over Other Apps Permission Check
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Display Over Other Apps",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Allows instant camera scanner on unlock",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 11.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                ) {
                                    Text("Enable", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Live Simulation & Testing Command Station
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "COMMAND & SIMULATION TESTING",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Test the exact unlock flow right now:",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, UnlockVerificationActivity::class.java).apply {
                                        putExtra(UnlockVerificationActivity.EXTRA_TRIGGER_TYPE, "TEST_OWNER")
                                        putExtra(UnlockVerificationActivity.EXTRA_SIMULATE_INTRUDER, false)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("test_owner_unlock_button")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Owner Unlock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(context, UnlockVerificationActivity::class.java).apply {
                                        putExtra(UnlockVerificationActivity.EXTRA_TRIGGER_TYPE, "TEST_INTRUDER")
                                        putExtra(UnlockVerificationActivity.EXTRA_SIMULATE_INTRUDER, true)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("test_intruder_alert_button")
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = "Alert", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Intruder Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 5. Audit & Security Activity Logs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SECURITY AUDIT LOGS",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${securityLogs.size} events",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            if (securityLogs.isEmpty()) {
                item {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = "Logs", tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No unlock events recorded yet", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(securityLogs) { log ->
                    SecurityLogItem(log)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Password Update Dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = {
                Text(
                    text = "Update Security Password",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "This password will be asked on screen with a 10s countdown if eye biometrics do not match.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("dialog_new_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput.isNotBlank()) {
                            repo.setSecurityPassword(newPasswordInput.trim())
                            refreshState()
                            showPasswordDialog = false
                            Toast.makeText(context, "Security password updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.testTag("save_new_password_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
fun SecurityLogItem(log: SecurityLog) {
    val isAuthorized = log.status == "AUTHORIZED_OWNER"
    val timeFormatted = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Surface(
        color = Color(0xFF0B1329),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAuthorized) Color(0xFF065F46) else Color(0xFF7F1D1D)
        ),
        modifier = Modifier.fillMaxWidth().testTag("log_item_${log.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Snapshot or icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                if (!log.snapshotPath.isNullOrEmpty() && File(log.snapshotPath).exists()) {
                    AsyncImage(
                        model = File(log.snapshotPath),
                        contentDescription = "Snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isAuthorized) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (isAuthorized) Color(0xFF34D399) else Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isAuthorized) "OWNER AUTHORIZED" else "INTRUDER / MISMATCH",
                        color = if (isAuthorized) Color(0xFF34D399) else Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = timeFormatted,
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Eyes Check: ${(log.eyeScore * 100).toInt()}% • Face Check: ${(log.faceScore * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = log.message,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
