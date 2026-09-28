package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.BiometricRepository
import com.example.receiver.UnlockReceiver

class EyeGuardForegroundService : Service() {
    private var internalUnlockReceiver: UnlockReceiver? = null

    companion object {
        private const val CHANNEL_ID = "eyeguard_protection_channel"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, EyeGuardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EyeGuardForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // Register dynamic receiver for USER_PRESENT to ensure 100% responsiveness on Android 10
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        internalUnlockReceiver = UnlockReceiver()
        registerReceiver(internalUnlockReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = BiometricRepository.getInstance(this)
        if (!repo.getOwnerBiometrics().isActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        internalUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EyeGuard Active Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device unlocks for owner eye and face verification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EyeGuard Biometric Shield Active")
            .setContentText("Monitoring unlock events for eye & face verification")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
