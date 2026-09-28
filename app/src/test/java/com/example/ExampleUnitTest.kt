package com.example

import com.example.biometrics.BiometricMatcher
import com.example.biometrics.ExtractedBiometrics
import com.example.data.OwnerBiometrics
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testOwnerVerificationSuccess() {
        val owner = OwnerBiometrics(
            isRegistered = true,
            isActive = true,
            securityPassword = "1234",
            eyeDistanceRatio = 0.42f,
            leftEyeToNoseRatio = 1.05f,
            rightEyeToNoseRatio = 1.05f,
            mouthToEyesRatio = 1.60f,
            eyeAspectRatio = 0.02f,
            faceAspectRatio = 0.85f
        )

        // Matching detected candidate
        val candidate = ExtractedBiometrics(
            eyeDistanceRatio = 0.425f,
            leftEyeToNoseRatio = 1.04f,
            rightEyeToNoseRatio = 1.06f,
            mouthToEyesRatio = 1.58f,
            eyeAspectRatio = 0.022f,
            faceAspectRatio = 0.84f,
            leftEyeOpenProb = 0.9f,
            rightEyeOpenProb = 0.9f,
            eyeCropBitmap = null,
            faceCropBitmap = null,
            isEyesClear = true
        )

        val result = BiometricMatcher.verifyIdentity(candidate, owner, null, null)
        assertTrue("Owner should be verified", result.isVerified)
        assertTrue("Eye check should pass", result.eyeCheckPassed)
        assertTrue("Face check should pass", result.faceCheckPassed)
    }

    @Test
    fun testIntruderVerificationFails() {
        val owner = OwnerBiometrics(
            isRegistered = true,
            isActive = true,
            securityPassword = "1234",
            eyeDistanceRatio = 0.42f,
            leftEyeToNoseRatio = 1.05f,
            rightEyeToNoseRatio = 1.05f,
            mouthToEyesRatio = 1.60f,
            eyeAspectRatio = 0.02f,
            faceAspectRatio = 0.85f
        )

        // Intruder with very different eye and face proportions
        val intruder = ExtractedBiometrics(
            eyeDistanceRatio = 0.22f,
            leftEyeToNoseRatio = 1.55f,
            rightEyeToNoseRatio = 1.65f,
            mouthToEyesRatio = 2.20f,
            eyeAspectRatio = 0.15f,
            faceAspectRatio = 1.25f,
            leftEyeOpenProb = 0.9f,
            rightEyeOpenProb = 0.9f,
            eyeCropBitmap = null,
            faceCropBitmap = null,
            isEyesClear = true
        )

        val result = BiometricMatcher.verifyIdentity(intruder, owner, null, null)
        assertFalse("Intruder must NOT be verified", result.isVerified)
        assertFalse("Eye check must fail for intruder", result.eyeCheckPassed)
    }
}
