package com.example.eyeSeeYou

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission

class VibrationManager(private val context: Context,private val preferencesManager: PreferencesManager) {

    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)
    private var lastVibration: Long = 0
    private val MIN_DELAY_MS = 2500L

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun shortVibration(force: Boolean = false) {
        if (force || preferencesManager.isPhoneVibrationEnabled()) {
            vibrate(500,force)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun longVibration(force: Boolean = false) {
        if (force || preferencesManager.isPhoneVibrationEnabled()) {
            vibrate(1000, force)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(duration: Long, force: Boolean = false) {
        try {
            val now = System.currentTimeMillis()
            if (!force /*&& text == lastMessage*/ && now - lastVibration < MIN_DELAY_MS) {
                //Ignored message
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e("VibrationError", "Errore durante la vibrazione: ${e.message}")
        }
    }
}
