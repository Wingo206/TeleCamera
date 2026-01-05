package com.example.telecamera.data.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var shutterSoundId: Int = 0
    private var focusSoundId: Int = 0
    private var soundsLoaded = false

    init {
        loadSounds()
    }

    private fun loadSounds() {
        // We'll use system-style sounds by generating simple beep tones
        // For a more polished app, you could include actual sound files in res/raw
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                soundsLoaded = true
            }
        }
    }

    fun playShutterFeedback() {
        vibrateShutter()
        // Note: For a real shutter sound, you would load a sound file
        // Using the camera shutter sound requires MediaActionSound
        playShutterSound()
    }

    fun playFocusFeedback() {
        vibrateFocus()
    }

    fun vibrateShutter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    SHUTTER_VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(SHUTTER_VIBRATION_DURATION_MS)
        }
    }

    fun vibrateFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    FOCUS_VIBRATION_DURATION_MS,
                    FOCUS_VIBRATION_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(FOCUS_VIBRATION_DURATION_MS)
        }
    }

    fun vibrateConfirmation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 50, 50, 50),
                    intArrayOf(0, 128, 0, 255),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
        }
    }

    private fun playShutterSound() {
        // Use MediaActionSound for the system camera shutter sound
        try {
            val mediaActionSound = android.media.MediaActionSound()
            mediaActionSound.play(android.media.MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            // Sound playback failed, continue silently
        }
    }

    fun release() {
        soundPool.release()
    }

    companion object {
        private const val SHUTTER_VIBRATION_DURATION_MS = 100L
        private const val FOCUS_VIBRATION_DURATION_MS = 30L
        private const val FOCUS_VIBRATION_AMPLITUDE = 64
    }
}

