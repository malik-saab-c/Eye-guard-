package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.receiver.DeviceAdminLockReceiver

class DeviceLockManager(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, DeviceAdminLockReceiver::class.java)

    fun isDeviceAdminActive(): Boolean {
        return dpm.isAdminActive(adminComponent)
    }

    fun getDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "EyeGuard requires Device Administrator permission to automatically lock your device if an unknown identity tries to unlock your phone."
            )
        }
    }

    /**
     * Immediately locks the device screen using Android Device Policy.
     */
    fun lockDeviceNow(): Boolean {
        return try {
            if (isDeviceAdminActive()) {
                dpm.lockNow()
                true
            } else {
                Log.w("DeviceLockManager", "Device admin not active. Cannot lock device.")
                false
            }
        } catch (e: Exception) {
            Log.e("DeviceLockManager", "Failed to lock device", e)
            false
        }
    }

    /**
     * Vibrate warning pattern (rapid pulses).
     */
    fun triggerWarningVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) {
            Log.e("DeviceLockManager", "Vibrate error", e)
        }
    }
}
