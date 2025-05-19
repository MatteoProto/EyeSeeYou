package com.example.eyeseeyou.presentation.util

import android.content.Context
import androidx.preference.PreferenceManager

object WatchPreferences {

    private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"

    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true) // true = default attivo
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }
}