package com.viberunning.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vibe_running_prefs", Context.MODE_PRIVATE)

    var useImperial: Boolean
        get() = prefs.getBoolean(KEY_USE_IMPERIAL, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_IMPERIAL, value).apply()

    companion object {
        private const val KEY_USE_IMPERIAL = "use_imperial"
    }
}
