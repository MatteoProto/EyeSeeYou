package com.example.eyeSeeYou

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission

class VibrationManager(private val context: Context) {

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun shortVibration() {
        vibrate(500)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun longVibration() {
        vibrate(1000)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(duration: Long) {
        try {
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
