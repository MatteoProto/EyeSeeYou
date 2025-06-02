package com.example.eyeSeeYou.managers

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission

class VibrationManager(private val context: Context, private val preferencesManager: PreferencesManager) {

    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)
    private var lastMessageTime: Long = 0
    private val MIN_DELAY_MS = 2500L

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun shortVibration(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastMessageTime < MIN_DELAY_MS) {
            return
        }
        if (force || preferencesManager.isPhoneVibrationEnabled()) {
            vibrate(200)
            lastMessageTime = System.currentTimeMillis()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun longVibration(force: Boolean = false) {
        if (force || preferencesManager.isPhoneVibrationEnabled()) {
            vibrate(500)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(duration: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator?.vibrate(effect, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e("VibrationError", "Error during vibration: ${e.message}")
        }
    }
}