package com.example.eyeseeyou.presentation

import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private var lastVibrationTime: Long = 0
    private var lastMessage: String? = null
    private val MIN_DELAY_MS = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Wearable.getMessageClient(this).addListener(this)
        Log.d("WEAR", "Wearable message listener avviato")
    }

    override fun onMessageReceived(event: MessageEvent) {
        val message = String(event.data)
        val now = System.currentTimeMillis()
        val vibrator = getSystemService(Vibrator::class.java)

        Log.d("WEAR", "Messaggio ricevuto: $message")

        if (vibrator?.hasVibrator() != true) {
            Log.w("WEAR", "Il dispositivo non supporta la vibrazione.")
            return
        }

        val isNewMessage = message != lastMessage
        val isDelayRespected = now - lastVibrationTime >= MIN_DELAY_MS

        if (isNewMessage || isDelayRespected) {
            vibrateOnce(vibrator)
            lastMessage = message
            lastVibrationTime = now
            Log.d("WEAR", "Vibrazione eseguita per: $message")
        } else {
            Log.d("WEAR", "Messaggio ignorato per debounce: $message")
        }
    }

    private fun vibrateOnce(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Wearable.getMessageClient(this).removeListener(this)
        Log.d("WEAR", "Wearable message listener rimosso")
    }
}