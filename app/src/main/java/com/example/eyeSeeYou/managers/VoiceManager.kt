package com.example.eyeSeeYou.managers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.eyeSeeYou.MainActivity
import com.example.eyeSeeYou.R
import java.util.Locale

class VoiceManager (
    private val context: MainActivity){

    lateinit var speechRecognizer: SpeechRecognizer
    lateinit var speechIntent: Intent

    fun setup() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.uppercase(Locale.getDefault()) ?: return

                val startCommands = context.resources.getStringArray(R.array.start).map { it.uppercase() }
                val stopCommands = context.resources.getStringArray(R.array.stop).map { it.uppercase() }
                val vibrationCommands = context.resources.getStringArray(R.array.vibration_commands).map { it.uppercase() }
                val disableVibrationCommands = context.resources.getStringArray(R.array.disable_vibration_commands).map { it.uppercase() }
                val watchVibrationCommands = context.resources.getStringArray(R.array.watch_vibration_commands).map { it.uppercase() }
                val disableWatchVibrationCommands = context.resources.getStringArray(R.array.disable_watch_vibration_commands).map { it.uppercase() }
                val speechCommands = context.resources.getStringArray(R.array.enable_speech_commands).map { it.uppercase() }
                val disableSpeechCommands = context.resources.getStringArray(R.array.disable_speech_commands).map { it.uppercase() }

                when {
                    startCommands.any { it in command } -> context.toggleARCore()
                    stopCommands.any { it in command } -> context.toggleARCore()
                    vibrationCommands.any { it in command } -> context.togglePhoneVibration()
                    disableVibrationCommands.any { it in command } -> context.togglePhoneVibration()
                    watchVibrationCommands.any { it in command } -> context.toggleWatchVibration()
                    disableWatchVibrationCommands.any { it in command } -> context.toggleWatchVibration()
                    speechCommands.any { it in command } -> context.toggleTTS()
                    disableSpeechCommands.any { it in command } -> context.toggleTTS()

                }

                speechRecognizer.startListening(speechIntent)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                speechRecognizer.startListening(speechIntent)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(speechIntent)
    }

    fun startListening() {
        speechRecognizer.startListening(speechIntent)
    }

    fun destroy() {
        speechRecognizer.destroy()
    }
}