/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.eyeSeeYou.helpers

import android.content.Context
import android.content.SharedPreferences

/**
 * A class providing persistent EIS preference across instances using `android.content.SharedPreferences`.
 */
class EisSettings {
    private var eisEnabled = true
    private var sharedPreferences: SharedPreferences? = null

    /** Creates shared preference entry for EIS setting.  */
    fun onCreate(context: Context) {
        sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCE_ID, Context.MODE_PRIVATE)
        eisEnabled = sharedPreferences!!.getBoolean(SHARED_PREFERENCE_EIS_ENABLED, false)
    }

    /** Returns saved EIS state.  */
    fun isEisEnabled(): Boolean {
        return eisEnabled
    }

    /** Sets and saves the EIS using `android.content.SharedPreferences`  */
    fun setEisEnabled(enable: Boolean) {
        if (enable == eisEnabled) {
            return
        }

        eisEnabled = enable
        val editor = sharedPreferences!!.edit()
        editor.putBoolean(SHARED_PREFERENCE_EIS_ENABLED, eisEnabled)
        editor.apply()
    }

    companion object {
        const val SHARED_PREFERENCE_ID: String = "SHARED_PREFERENCE_EIS_OPTIONS"
        const val SHARED_PREFERENCE_EIS_ENABLED: String = "eis_enabled"
    }
}
