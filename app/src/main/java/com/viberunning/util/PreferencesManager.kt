package com.viberunning.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vibe_running_prefs", Context.MODE_PRIVATE)

    var useImperial: Boolean
        get() = prefs.getBoolean(KEY_USE_IMPERIAL, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_IMPERIAL, value).apply()

    // Height in inches (stored internally, converted for display)
    var heightInches: Float
        get() = prefs.getFloat(KEY_HEIGHT_INCHES, 0f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_INCHES, value).apply()

    // Weight in pounds (stored internally, converted for display)
    var weightLbs: Float
        get() = prefs.getFloat(KEY_WEIGHT_LBS, 0f)
        set(value) {
            prefs.edit()
                .putFloat(KEY_WEIGHT_LBS, value)
                .putLong(KEY_WEIGHT_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }

    var weightUpdatedAt: Long
        get() = prefs.getLong(KEY_WEIGHT_UPDATED_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_WEIGHT_UPDATED_AT, value).apply()

    val hasProfile: Boolean
        get() = heightInches > 0f && weightLbs > 0f

    val isWeightStale: Boolean
        get() {
            if (weightUpdatedAt == 0L) return true
            val daysSinceUpdate = (System.currentTimeMillis() - weightUpdatedAt) / (1000L * 60 * 60 * 24)
            return daysSinceUpdate >= WEIGHT_PROMPT_DAYS
        }

    // BMI = (weight in lbs × 703) / (height in inches)²
    val bmi: Float
        get() {
            if (heightInches <= 0f || weightLbs <= 0f) return 0f
            return (weightLbs * 703f) / (heightInches * heightInches)
        }

    val bmiCategory: String
        get() = when {
            bmi <= 0f -> "—"
            bmi < 18.5f -> "Underweight"
            bmi < 25f -> "Normal"
            bmi < 30f -> "Overweight"
            else -> "Obese"
        }

    companion object {
        private const val KEY_USE_IMPERIAL = "use_imperial"
        private const val KEY_HEIGHT_INCHES = "height_inches"
        private const val KEY_WEIGHT_LBS = "weight_lbs"
        private const val KEY_WEIGHT_UPDATED_AT = "weight_updated_at"
        const val WEIGHT_PROMPT_DAYS = 14
    }
}
