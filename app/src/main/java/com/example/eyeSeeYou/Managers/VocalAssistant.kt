package com.example.eyeSeeYou.Managers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.eyeSeeYou.R
import com.example.eyeSeeYou.helpers.VoiceMessage
import java.util.Locale

class VocalAssistant(private val context: Context, private val preferencesManager: PreferencesManager) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

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
                Log.e("VocalAssistant", "Not supported language.")
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force && now - lastMessageTime < MIN_DELAY_MS) {
            return
        }

        if ((preferencesManager.isTTSEnabled() || force) && isReady) {
            val locale = context.resources.configuration.locales[0]
            val result = tts?.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VocalAssistant", "Not supported language: $locale")
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastMessageTime = now
            Log.d("VocalAssistant", "Talked: $text with language $locale")
        } else if (!isReady) {
            Log.e("VocalAssistant", "TTS not ready.")
        }
    }

    fun playMessage(type: VoiceMessage, force: Boolean = false) {
        val message = context.getString(type.resId)
        speak(message, force)
    }

    fun shutdown() {
        if (isReady) {
            tts?.stop()
            tts?.shutdown()
            isReady = false
            Log.d("VocalAssistant", "TTS terminated.")
        } else {
            Log.e("VocalAssistant", "TTS not initialized.")
        }
    }
}