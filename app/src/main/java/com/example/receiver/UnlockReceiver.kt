package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.BiometricRepository
import com.example.service.EyeGuardForegroundService
import com.example.ui.UnlockVerificationActivity

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("UnlockReceiver", "Received action: $action")

        val repo = BiometricRepository.getInstance(context)
        val owner = repo.getOwnerBiometrics()

        if (!owner.isRegistered || !owner.isActive) {
            Log.d("UnlockReceiver", "EyeGuard not active or registered. Ignoring.")
            return
        }

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                EyeGuardForegroundService.startService(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                // Device was unlocked by user!
                // Launch UnlockVerificationActivity immediately to verify eyes & face
                val verifyIntent = Intent(context, UnlockVerificationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(UnlockVerificationActivity.EXTRA_TRIGGER_TYPE, "REAL_UNLOCK")
                }
                context.startActivity(verifyIntent)
            }
        }
    }
}
