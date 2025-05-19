package com.example.eyeseeyou.presentation

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.eyeseeyou.presentation.util.WatchPreferences
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class MessageListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearMessageListener"
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val command = String(messageEvent.data)

        if (messageEvent.path == "/vibration_command") {
            Log.d(TAG, "Received vibration command: $command")

            when (command) {
                "sx" -> {
                    if (isVibrationEnabled()) {
                        Log.d(TAG, "Left Vibration.")
                        triggerVibration(longArrayOf(0, 100, 150, 500))
                    }
                }

                "dx" -> {
                    if (isVibrationEnabled()) {
                        Log.d(TAG, "Right vibration.")
                        triggerVibration(longArrayOf(0, 500, 150, 100))
                    }
                }

                "up" -> {
                    if (isVibrationEnabled()) {
                        Log.d(TAG, "Center vibration.")
                        triggerVibration(longArrayOf(0, 300))
                    }
                }

                "down" -> {
                    if (isVibrationEnabled()) {
                        Log.d(TAG, "Low vibration.")
                        triggerVibration(longArrayOf(0, 300, 150, 300))
                    }
                }

                "dangerous" -> {
                    if (isVibrationEnabled()) {
                        Log.d(TAG, "Stop vibration")
                        triggerVibration(longArrayOf(0, 1000))
                    }
                }

                "generics" -> {
                    if (isVibrationEnabled()) {
                        Log.d(TAG, "Generic vibration.")
                        triggerVibration(longArrayOf(0, 200))
                    }
                }
                else -> {
                    Log.w(TAG, "Wrong command: $command")
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun triggerVibration(pattern: LongArray) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Not supported vibration.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(vibrationEffect)
            Log.d(TAG, "Vibration started waveform (API >= 26): ${pattern.joinToString()}")
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
            Log.d(TAG, "Vibration started waveform legacy (API < 26): ${pattern.joinToString()}")
        }
    }

    private fun isVibrationEnabled(): Boolean {
        return WatchPreferences.isVibrationEnabled(applicationContext)
    }
}