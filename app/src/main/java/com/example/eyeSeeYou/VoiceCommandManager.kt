package com.example.eyeSeeYou

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.Toast

class VoiceCommandManager(
    private val context: Context,
    private val language: String,
    private val button: Button,
    private val onCommandRecognized: (String) -> Unit
) {
    private val MAX_RETRIES = 1
    private var retryCount = 0
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Parla ora…")
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
    }

    private var isListening = false

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                button.animate().alpha(1f).setDuration(100).start()
                isListening = false
                val spokenText = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: return
                onCommandRecognized(spokenText)
            }

            override fun onError(error: Int) {
                button.animate().alpha(1f).setDuration(100).start()
                Log.e("VoiceCommandManager", "Speech recognition error code: $error")
                isListening = false

                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Handler(Looper.getMainLooper()).postDelayed({
                        startListening()
                    }, 500)
                } else {
                    Toast.makeText(context, "Errore riconoscimento vocale. Riprova più tardi.", Toast.LENGTH_SHORT).show()
                    retryCount = 0
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (!isListening) {
            retryCount = 0
            speechRecognizer.startListening(recognizerIntent)
            isListening = true
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
        }
    }

    fun stop() {
        speechRecognizer.destroy()
    }
}