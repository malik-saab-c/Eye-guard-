package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class OwnerBiometrics(
    val isRegistered: Boolean = false,
    val isActive: Boolean = false,
    val securityPassword: String = "1234",
    val eyeDistanceRatio: Float = 0f,
    val leftEyeToNoseRatio: Float = 0f,
    val rightEyeToNoseRatio: Float = 0f,
    val mouthToEyesRatio: Float = 0f,
    val eyeAspectRatio: Float = 0f,
    val faceAspectRatio: Float = 0f,
    val eyeImagePath: String? = null,
    val faceImagePath: String? = null,
    val registeredTimestamp: Long = 0L
)

data class SecurityLog(
    val id: String,
    val timestamp: Long,
    val status: String, // "AUTHORIZED_OWNER", "UNKNOWN_IDENTITY_ALERT", "SECURITY_LOCKED", "PASSWORD_OVERRIDE"
    val eyeScore: Float,
    val faceScore: Float,
    val message: String,
    val snapshotPath: String? = null
)

class BiometricRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("eyeguard_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_REGISTERED = "is_registered"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_SECURITY_PASSWORD = "security_password"
        private const val KEY_EYE_DISTANCE_RATIO = "eye_distance_ratio"
        private const val KEY_LEFT_EYE_NOSE_RATIO = "left_eye_nose_ratio"
        private const val KEY_RIGHT_EYE_NOSE_RATIO = "right_eye_nose_ratio"
        private const val KEY_MOUTH_EYES_RATIO = "mouth_eyes_ratio"
        private const val KEY_EYE_ASPECT_RATIO = "eye_aspect_ratio"
        private const val KEY_FACE_ASPECT_RATIO = "face_aspect_ratio"
        private const val KEY_EYE_IMAGE = "eye_image_path"
        private const val KEY_FACE_IMAGE = "face_image_path"
        private const val KEY_TIMESTAMP = "registered_timestamp"
        private const val KEY_LOGS = "security_logs_json"

        @Volatile
        private var INSTANCE: BiometricRepository? = null

        fun getInstance(context: Context): BiometricRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BiometricRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getOwnerBiometrics(): OwnerBiometrics {
        return OwnerBiometrics(
            isRegistered = prefs.getBoolean(KEY_IS_REGISTERED, false),
            isActive = prefs.getBoolean(KEY_IS_ACTIVE, false),
            securityPassword = prefs.getString(KEY_SECURITY_PASSWORD, "1234") ?: "1234",
            eyeDistanceRatio = prefs.getFloat(KEY_EYE_DISTANCE_RATIO, 0f),
            leftEyeToNoseRatio = prefs.getFloat(KEY_LEFT_EYE_NOSE_RATIO, 0f),
            rightEyeToNoseRatio = prefs.getFloat(KEY_RIGHT_EYE_NOSE_RATIO, 0f),
            mouthToEyesRatio = prefs.getFloat(KEY_MOUTH_EYES_RATIO, 0f),
            eyeAspectRatio = prefs.getFloat(KEY_EYE_ASPECT_RATIO, 0f),
            faceAspectRatio = prefs.getFloat(KEY_FACE_ASPECT_RATIO, 0f),
            eyeImagePath = prefs.getString(KEY_EYE_IMAGE, null),
            faceImagePath = prefs.getString(KEY_FACE_IMAGE, null),
            registeredTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        )
    }

    fun saveOwnerBiometrics(biometrics: OwnerBiometrics) {
        prefs.edit()
            .putBoolean(KEY_IS_REGISTERED, biometrics.isRegistered)
            .putBoolean(KEY_IS_ACTIVE, biometrics.isActive)
            .putString(KEY_SECURITY_PASSWORD, biometrics.securityPassword)
            .putFloat(KEY_EYE_DISTANCE_RATIO, biometrics.eyeDistanceRatio)
            .putFloat(KEY_LEFT_EYE_NOSE_RATIO, biometrics.leftEyeToNoseRatio)
            .putFloat(KEY_RIGHT_EYE_NOSE_RATIO, biometrics.rightEyeToNoseRatio)
            .putFloat(KEY_MOUTH_EYES_RATIO, biometrics.mouthToEyesRatio)
            .putFloat(KEY_EYE_ASPECT_RATIO, biometrics.eyeAspectRatio)
            .putFloat(KEY_FACE_ASPECT_RATIO, biometrics.faceAspectRatio)
            .putString(KEY_EYE_IMAGE, biometrics.eyeImagePath)
            .putString(KEY_FACE_IMAGE, biometrics.faceImagePath)
            .putLong(KEY_TIMESTAMP, biometrics.registeredTimestamp)
            .apply()
    }

    fun setGuardActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ACTIVE, active).apply()
    }

    fun setSecurityPassword(password: String) {
        prefs.edit().putString(KEY_SECURITY_PASSWORD, password).apply()
    }

    fun saveBitmap(bitmap: Bitmap, filename: String): String? {
        return try {
            val file = File(context.filesDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("BiometricRepository", "Error saving bitmap $filename", e)
            null
        }
    }

    fun loadBitmap(path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun addLog(log: SecurityLog) {
        try {
            val logs = getLogs().toMutableList()
            logs.add(0, log) // newest first
            // keep latest 30 logs
            val trimmed = logs.take(30)
            val jsonArray = JSONArray()
            trimmed.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("timestamp", item.timestamp)
                obj.put("status", item.status)
                obj.put("eyeScore", item.eyeScore.toDouble())
                obj.put("faceScore", item.faceScore.toDouble())
                obj.put("message", item.message)
                obj.put("snapshotPath", item.snapshotPath ?: "")
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_LOGS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("BiometricRepository", "Error saving security log", e)
        }
    }

    fun getLogs(): List<SecurityLog> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        val list = mutableListOf<SecurityLog>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SecurityLog(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        status = obj.getString("status"),
                        eyeScore = obj.getDouble("eyeScore").toFloat(),
                        faceScore = obj.getDouble("faceScore").toFloat(),
                        message = obj.getString("message"),
                        snapshotPath = obj.optString("snapshotPath").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BiometricRepository", "Error parsing logs", e)
        }
        return list
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}
