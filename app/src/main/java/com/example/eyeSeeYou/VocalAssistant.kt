package com.example.eyeSeeYou

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VocalAssistant(private val context: Context, private val preferencesManager: PreferencesManager) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private var lastMessage: String? = null
    private var lastMessageTime: Long = 0
    private val MIN_DELAY_MS = 2000L  // 2 secondi tra messaggi ripetuti

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val language = preferencesManager.getPreferredLanguage()
            val locale = when (language) {
                "en" -> Locale.ENGLISH
                "it" -> Locale.ITALIAN
                else -> Locale.ENGLISH
            }
            val result = tts?.setLanguage(locale)
            isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isReady) {
                // Saluto di benvenuto obbligatorio all'avvio
                speak("Benvenuto in EyeSeeYou.", force = true)
            } else {
                Log.e("VocalAssistant", "Lingua non supportata o dati mancanti.")
            }
        } else {
            Log.e("VocalAssistant", "Inizializzazione TTS fallita.")
        }
    }

    fun speak(text: String, force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force && text == lastMessage && now - lastMessageTime < MIN_DELAY_MS) {
            Log.d("VocalAssistant", "Messaggio '$text' ignorato per evitare ripetizione.")
            return
        }

        if ((preferencesManager.isTTSEnabled() || force) && isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastMessage = text
            lastMessageTime = now
            Log.d("VocalAssistant", "Parlato: $text")
        } else if (!isReady) {
            Log.e("VocalAssistant", "TTS non pronto.")
        }
    }

    fun playMessage(type: MessageType, force: Boolean = false) {
        when (type) {
            MessageType.WELCOME -> speak("Welcome to Eye-See-You.", force)
            MessageType.WARNING_STEP -> speak("Warning, there's a step ahead of you.", force)
            MessageType.WARNING_DESCENT -> speak("Caution, a descent is ahead.", force)
            MessageType.WARNING_ASCENT -> speak("Caution, an ascent is ahead.", force)
            MessageType.WARNING_OBSTACLE -> speak("Warning, obstacle very close.", force)
            MessageType.WARNING -> speak("Warning, obstacle.", force)
        }
    }

    fun shutdown() {
        if (isReady) {
            tts?.stop()
            tts?.shutdown()
            isReady = false
            Log.d("VocalAssistant", "TTS terminato.")
        } else {
            Log.e("VocalAssistant", "TTS non era inizializzato.")
        }
    }
}

enum class MessageType {
    WELCOME,
    WARNING_STEP,
    WARNING_DESCENT,
    WARNING_ASCENT,
    WARNING_OBSTACLE,
    WARNING
}