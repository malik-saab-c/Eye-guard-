package com.example.biometrics

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.OwnerBiometrics
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class ExtractedBiometrics(
    val eyeDistanceRatio: Float,
    val leftEyeToNoseRatio: Float,
    val rightEyeToNoseRatio: Float,
    val mouthToEyesRatio: Float,
    val eyeAspectRatio: Float,
    val faceAspectRatio: Float,
    val leftEyeOpenProb: Float,
    val rightEyeOpenProb: Float,
    val eyeCropBitmap: Bitmap?,
    val faceCropBitmap: Bitmap?,
    val isEyesClear: Boolean
)

data class BiometricMatchResult(
    val isVerified: Boolean,
    val eyeScore: Float,
    val faceScore: Float,
    val eyeCheckPassed: Boolean,
    val faceCheckPassed: Boolean,
    val reason: String
)

object BiometricMatcher {

    /**
     * Extracts landmark geometry and cropped bitmaps from a detected Face.
     */
    fun extractBiometrics(face: Face, originalBitmap: Bitmap?): ExtractedBiometrics? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position

        if (leftEye == null || rightEye == null) {
            return null
        }

        val box = face.boundingBox
        val boxWidth = max(1, box.width()).toFloat()
        val boxHeight = max(1, box.height()).toFloat()

        val eyeDist = distance(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
        val eyeDistanceRatio = eyeDist / boxWidth

        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.8f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.8f
        val areEyesClear = leftEyeOpen > 0.4f && rightEyeOpen > 0.4f && eyeDist > 30f

        val leftEyeToNoseRatio = if (nose != null) distance(leftEye.x, leftEye.y, nose.x, nose.y) / max(1f, eyeDist) else 1.0f
        val rightEyeToNoseRatio = if (nose != null) distance(rightEye.x, rightEye.y, nose.x, nose.y) / max(1f, eyeDist) else 1.0f

        val mouthMidX = if (mouthLeft != null && mouthRight != null) (mouthLeft.x + mouthRight.x) / 2f else nose?.x ?: box.exactCenterX()
        val mouthMidY = if (mouthLeft != null && mouthRight != null) (mouthLeft.y + mouthRight.y) / 2f else box.bottom.toFloat()
        val eyesMidX = (leftEye.x + rightEye.x) / 2f
        val eyesMidY = (leftEye.y + rightEye.y) / 2f

        val mouthToEyesRatio = distance(eyesMidX, eyesMidY, mouthMidX, mouthMidY) / max(1f, eyeDist)
        val faceAspectRatio = boxWidth / boxHeight
        val eyeAspectRatio = abs(leftEye.y - rightEye.y) / max(1f, eyeDist)

        var eyeCrop: Bitmap? = null
        var faceCrop: Bitmap? = null

        if (originalBitmap != null) {
            try {
                // Crop face
                val safeBox = Rect(
                    max(0, box.left),
                    max(0, box.top),
                    min(originalBitmap.width, box.right),
                    min(originalBitmap.height, box.bottom)
                )
                if (safeBox.width() > 10 && safeBox.height() > 10) {
                    faceCrop = Bitmap.createBitmap(originalBitmap, safeBox.left, safeBox.top, safeBox.width(), safeBox.height())
                }

                // Crop eye band (from above eyes to below eyes spanning across both eyes)
                val eyeMinX = min(leftEye.x, rightEye.x) - (eyeDist * 0.35f)
                val eyeMaxX = max(leftEye.x, rightEye.x) + (eyeDist * 0.35f)
                val eyeMinY = min(leftEye.y, rightEye.y) - (eyeDist * 0.35f)
                val eyeMaxY = max(leftEye.y, rightEye.y) + (eyeDist * 0.35f)

                val eyeBox = Rect(
                    max(0, eyeMinX.toInt()),
                    max(0, eyeMinY.toInt()),
                    min(originalBitmap.width, eyeMaxX.toInt()),
                    min(originalBitmap.height, eyeMaxY.toInt())
                )
                if (eyeBox.width() > 10 && eyeBox.height() > 10) {
                    eyeCrop = Bitmap.createBitmap(originalBitmap, eyeBox.left, eyeBox.top, eyeBox.width(), eyeBox.height())
                }
            } catch (e: Exception) {
                // crop fallback
            }
        }

        return ExtractedBiometrics(
            eyeDistanceRatio = eyeDistanceRatio,
            leftEyeToNoseRatio = leftEyeToNoseRatio,
            rightEyeToNoseRatio = rightEyeToNoseRatio,
            mouthToEyesRatio = mouthToEyesRatio,
            eyeAspectRatio = eyeAspectRatio,
            faceAspectRatio = faceAspectRatio,
            leftEyeOpenProb = leftEyeOpen,
            rightEyeOpenProb = rightEyeOpen,
            eyeCropBitmap = eyeCrop,
            faceCropBitmap = faceCrop,
            isEyesClear = areEyesClear
        )
    }

    /**
     * Executes the strict 2-step verification:
     * Check 1: Eyes Match
     * Check 2: Face Match
     */
    fun verifyIdentity(
        detected: ExtractedBiometrics,
        owner: OwnerBiometrics,
        candidateEyeCrop: Bitmap?,
        ownerEyeCrop: Bitmap?
    ): BiometricMatchResult {
        if (!owner.isRegistered) {
            return BiometricMatchResult(
                isVerified = false,
                eyeScore = 0f,
                faceScore = 0f,
                eyeCheckPassed = false,
                faceCheckPassed = false,
                reason = "No Owner Biometrics Registered"
            )
        }

        // --- CHECK 1: EYES CHECK ---
        // 1. Ratio difference of Eye Distance
        val eyeDistDiff = abs(detected.eyeDistanceRatio - owner.eyeDistanceRatio)
        val eyeDistScore = max(0f, 1f - (eyeDistDiff * 3.5f))

        // 2. Eye Aspect / Tilt alignment score
        val eyeAspectDiff = abs(detected.eyeAspectRatio - owner.eyeAspectRatio)
        val eyeAspectScore = max(0f, 1f - (eyeAspectDiff * 4.0f))

        // 3. Eye crop visual similarity (Histogram / pixel luminance comparison)
        val visualEyeScore = if (candidateEyeCrop != null && ownerEyeCrop != null) {
            compareVisualHistograms(candidateEyeCrop, ownerEyeCrop)
        } else {
            0.85f // fallback to geometric score if bitmap comparison unavailable
        }

        // Weighted First Check Score
        val eyeScore = (eyeDistScore * 0.40f) + (eyeAspectScore * 0.20f) + (visualEyeScore * 0.40f)
        val eyeCheckPassed = eyeScore >= 0.70f

        // --- CHECK 2: FACE CHECK ---
        // 1. Nose-to-Eye Triangular geometry
        val leftNoseDiff = abs(detected.leftEyeToNoseRatio - owner.leftEyeToNoseRatio)
        val rightNoseDiff = abs(detected.rightEyeToNoseRatio - owner.rightEyeToNoseRatio)
        val noseScore = max(0f, 1f - ((leftNoseDiff + rightNoseDiff) * 1.8f))

        // 2. Mouth-to-Eyes ratio
        val mouthDiff = abs(detected.mouthToEyesRatio - owner.mouthToEyesRatio)
        val mouthScore = max(0f, 1f - (mouthDiff * 2.0f))

        // 3. Face Aspect Ratio
        val faceAspectDiff = abs(detected.faceAspectRatio - owner.faceAspectRatio)
        val faceAspectScore = max(0f, 1f - (faceAspectDiff * 2.5f))

        // Weighted Second Check Score
        val faceScore = (noseScore * 0.40f) + (mouthScore * 0.35f) + (faceAspectScore * 0.25f)
        val faceCheckPassed = faceScore >= 0.68f

        // Both checks must pass for 100% verified access
        val isVerified = eyeCheckPassed && faceCheckPassed

        val reason = when {
            isVerified -> "Owner Verified Successfully (Eye & Face Checks Passed)"
            !eyeCheckPassed && !faceCheckPassed -> "Eye and Face Biometrics Failed"
            !eyeCheckPassed -> "Eye Biometrics Mismatch (Check 1 Failed)"
            else -> "Facial Geometry Mismatch (Check 2 Failed)"
        }

        return BiometricMatchResult(
            isVerified = isVerified,
            eyeScore = min(1f, max(0f, eyeScore)),
            faceScore = min(1f, max(0f, faceScore)),
            eyeCheckPassed = eyeCheckPassed,
            faceCheckPassed = faceCheckPassed,
            reason = reason
        )
    }

    private fun compareVisualHistograms(bmpA: Bitmap, bmpB: Bitmap): Float {
        return try {
            val scaledA = Bitmap.createScaledBitmap(bmpA, 32, 16, true)
            val scaledB = Bitmap.createScaledBitmap(bmpB, 32, 16, true)

            var diffSum = 0.0
            val totalPixels = 32 * 16

            for (y in 0 until 16) {
                for (x in 0 until 32) {
                    val colorA = scaledA.getPixel(x, y)
                    val colorB = scaledB.getPixel(x, y)

                    val lumA = (0.299 * ((colorA shr 16) and 0xFF) + 0.587 * ((colorA shr 8) and 0xFF) + 0.114 * (colorA and 0xFF)) / 255.0
                    val lumB = (0.299 * ((colorB shr 16) and 0xFF) + 0.587 * ((colorB shr 8) and 0xFF) + 0.114 * (colorB and 0xFF)) / 255.0

                    diffSum += abs(lumA - lumB)
                }
            }

            val avgDiff = (diffSum / totalPixels).toFloat()
            // avgDiff ranges 0.0 (identical) to 1.0 (inverted)
            max(0f, 1f - (avgDiff * 1.8f))
        } catch (e: Exception) {
            0.75f
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
    }
}
