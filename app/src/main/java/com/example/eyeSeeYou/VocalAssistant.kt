package com.example.eyeSeeYou

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*
import com.example.eyeSeeYou.helpers.Zones

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

    fun getObstacleMessage(zones: Set<Zones>): String {
        if (zones.isEmpty()) return ""

        val vertical = when {
            Zones.LOW in zones -> "basso"
            Zones.HIGH in zones -> "alto"
            Zones.CENTER in zones -> "davanti"
            else -> ""
        }

        val horizontal = when {
            Zones.LEFT_WALL in zones -> "oggetto a sinistra"
            Zones.RIGHT_WALL in zones -> "oggetto a destra"
            Zones.LEFT in zones -> "a sinistra"
            Zones.RIGHT in zones -> "a destra"
            else -> ""
        }

        if (horizontal.contains("oggetto")) {
            return "ostacolo $horizontal"
        }

        return when {
            vertical.isNotEmpty() && horizontal.isNotEmpty() -> "ostacolo $vertical $horizontal"
            vertical.isNotEmpty() -> "ostacolo $vertical"
            horizontal.isNotEmpty() -> "ostacolo $horizontal"
            else -> "ostacolo davanti"
        }
    }

    fun playMessage(type: MessageType, force: Boolean = false) {
        when (type) {
            MessageType.WELCOME -> speak("Benvenuto in EyeSeeYou.", force)
            MessageType.WARNING_STEPDOWN -> speak("Attenzione, gradino in discesa.", force)
            MessageType.WARNING_STEPUP -> speak("Attenzione, gradino in salita.", force)
            MessageType.WARNING_OBSTACLE -> speak("Attenzione, ostacolo molto vicino.", force)
            MessageType.WARNING -> speak("Attenzione, ostacolo rilevato.", force)
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
    WARNING_STEPUP,
    WARNING_STEPDOWN,
    WARNING_OBSTACLE,
    WARNING
}



