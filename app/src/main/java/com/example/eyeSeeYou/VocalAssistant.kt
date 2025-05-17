package com.example.eyeSeeYou

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.eyeSeeYou.helpers.VoiceMessage
import java.util.*
import com.example.eyeSeeYou.helpers.Zones

class VocalAssistant(private val context: Context, private val preferencesManager: PreferencesManager) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private var lastMessage: String? = null
    private var lastMessageTime: Long = 0
    private val MIN_DELAY_MS = 3000L

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
            isReady =
                result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isReady) {
                val welcomeMessage = context.getString(R.string.welcome)
                speak(welcomeMessage, force = true)
            } else {
                Log.e("VocalAssistant", "Lingua non supportata o dati mancanti.")
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force /*&& text == lastMessage*/ && now - lastMessageTime < MIN_DELAY_MS) {
            //Ignored message
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

    /*
        fun getObstacleMessage(zones: Set<Zones>): String {
            if (zones.isEmpty()) return ""

            val vertical = when {
                Zones.LOW in zones -> context.getString(R.string.vertical_low)
                Zones.HIGH in zones -> context.getString(R.string.vertical_high)
                Zones.CENTER in zones -> context.getString(R.string.vertical_center)
                else -> ""
            }

            val horizontal = when {
                Zones.LEFT_WALL in zones -> context.getString(R.string.horizontal_object_left)
                Zones.RIGHT_WALL in zones -> context.getString(R.string.horizontal_object_right)
                Zones.LEFT in zones -> context.getString(R.string.horizontal_left)
                Zones.RIGHT in zones -> context.getString(R.string.horizontal_right)
                else -> ""
            }

            return when {
                vertical.isNotEmpty() && horizontal.isNotEmpty() -> context.getString(R.string.obstacle_vertical_horizontal, vertical, horizontal)
                vertical.isNotEmpty() -> context.getString(R.string.obstacle_vertical, vertical)
                horizontal.isNotEmpty() -> context.getString(R.string.obstacle_horizontal, horizontal)
                else -> context.getString(R.string.obstacle_center)
            }
        }
    */

    fun playMessage(type: VoiceMessage, force: Boolean = false) {
        val message = context.getString(type.resId)
        speak(message, force)
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



