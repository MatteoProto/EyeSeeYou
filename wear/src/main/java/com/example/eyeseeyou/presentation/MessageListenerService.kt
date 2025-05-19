package com.example.eyeseeyou.presentation

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class MessageListenerService : WearableListenerService() {

    private val VIBRATE_MESSAGE_PATH = "/vibrate_request"
    private val TAG = "WearMessageListener"
    private val VIBRATION_DURATION_MS = 500L // Durata della vibrazione in millisecondi

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Messaggio ricevuto: ${messageEvent.path}")

        // Controlla se il path del messaggio corrisponde a quello che ci aspettiamo
        if (messageEvent.path == VIBRATE_MESSAGE_PATH) {
            Log.d(TAG, "Path corrisponde, avvio vibrazione...")
            val data = messageEvent.data
            val command = String(data, Charsets.UTF_8)
            when(command){
                "sx" -> triggerVibration(longArrayOf(0, 500, 200, 100, 200, 100))
                "dx" -> triggerVibration(longArrayOf(0, 200, 500, 100, 500, 100))
                else -> println("Comando vibrazione sconosciuto: $command")
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun triggerVibration(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Per Android 12 (API 31) e successivi
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            // Deprecato da API 31, ma necessario per compatibilità
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // Controlla se il dispositivo ha un vibratore
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Il dispositivo non supporta la vibrazione.")
            return
        }

        // Crea l'effetto di vibrazione
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Per Android Oreo (API 26) e successivi
            //val vibrationEffect = VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            val vibrationEffect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(vibrationEffect)
            Log.d(TAG, "Vibrazione avviata (API >= 26)")
        } else {
            // Deprecato da API 26, ma necessario per compatibilità
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_DURATION_MS)
            Log.d(TAG, "Vibrazione avviata (API < 26)")
        }
    }
}