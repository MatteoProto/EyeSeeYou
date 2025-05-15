package com.example.eyeSeeYou

import android.content.Context

class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "tts_prefs"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
        private const val KEY_WATCH_VIBRATION_ACTIVE = "watch_vibration_active"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Verifica se la sintesi vocale è attiva (default true)
    fun isTTSEnabled(): Boolean = prefs.getBoolean(KEY_TTS_ENABLED, true)

    // Imposta lo stato della sintesi vocale
    fun setTTSEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()

    // Recupera la lingua preferita (default "en")
    fun getPreferredLanguage(): String =
        prefs.getString(KEY_PREFERRED_LANGUAGE, "en") ?: "en"

    // Imposta la lingua preferita
    fun setPreferredLanguage(language: String) =
        prefs.edit().putString(KEY_PREFERRED_LANGUAGE, language).apply()

    // Verifica se la vibrazione orologio è attiva (default false)
    fun isWatchVibrationActive(): Boolean =
        prefs.getBoolean(KEY_WATCH_VIBRATION_ACTIVE, false)

    // Imposta lo stato della vibrazione dell'orologio
    fun setWatchVibrationActive(isActive: Boolean) =
        prefs.edit().putBoolean(KEY_WATCH_VIBRATION_ACTIVE, isActive).apply()

    // Resetta tutte le preferenze (utile per test o ripristino)
    fun clearAll() = prefs.edit().clear().apply()
}