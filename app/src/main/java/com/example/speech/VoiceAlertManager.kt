package com.example.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceAlertManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeech: String? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.95f) // slightly authoritative/clear security voice
                tts?.setSpeechRate(0.95f)
                isReady = true
                pendingSpeech?.let { text ->
                    speak(text)
                    pendingSpeech = null
                }
            } else {
                Log.e("VoiceAlertManager", "TTS initialization failed status: $status")
            }
        }
    }

    fun speakUnknownIdentityAlert() {
        speak("Unknown identity detected! What is security password?")
    }

    fun speak(text: String) {
        if (!isReady || tts == null) {
            pendingSpeech = text
            return
        }

        try {
            // Request high speech volume for critical security alert
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol < (maxVol * 0.7f).toInt()) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85f).toInt(), 0)
                }
            }

            val params = android.os.Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "SECURITY_ALERT_UTTERANCE")
        } catch (e: Exception) {
            Log.e("VoiceAlertManager", "Error speaking alert", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (_: Exception) {}
    }
}
