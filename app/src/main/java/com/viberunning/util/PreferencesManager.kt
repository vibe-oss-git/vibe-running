package com.viberunning.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vibe_running_prefs", Context.MODE_PRIVATE)

    var useImperial: Boolean
        get() = prefs.getBoolean(KEY_USE_IMPERIAL, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_IMPERIAL, value).apply()

    // Minutes without movement before a run auto-pauses
    var inactivityPauseMinutes: Int
        get() = prefs.getInt(KEY_INACTIVITY_PAUSE_MINUTES, DEFAULT_INACTIVITY_PAUSE_MINUTES)
        set(value) = prefs.edit().putInt(KEY_INACTIVITY_PAUSE_MINUTES, value).apply()

    // Minutes without movement before a run is ended. Never less than the pause time.
    var inactivityExitMinutes: Int
        get() = prefs.getInt(KEY_INACTIVITY_EXIT_MINUTES, DEFAULT_INACTIVITY_EXIT_MINUTES)
            .coerceAtLeast(inactivityPauseMinutes)
        set(value) = prefs.edit().putInt(KEY_INACTIVITY_EXIT_MINUTES, value).apply()

    // SustainedSpeed.ALGORITHM_VERSION that saved runs' max speeds were last recalculated with
    var maxSpeedAlgorithmVersion: Int
        get() = prefs.getInt(KEY_MAX_SPEED_ALGORITHM_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_MAX_SPEED_ALGORITHM_VERSION, value).apply()

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

    // Restores a weight from a backup, keeping when it was recorded
    fun restoreWeight(lbs: Float, updatedAt: Long) {
        prefs.edit()
            .putFloat(KEY_WEIGHT_LBS, lbs)
            .putLong(KEY_WEIGHT_UPDATED_AT, updatedAt)
            .apply()
    }

    // Sex: "male" or "female" (stored as string)
    var sex: String
        get() = prefs.getString(KEY_SEX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SEX, value).apply()

    // Date of birth as epoch millis
    var dateOfBirthMillis: Long
        get() = prefs.getLong(KEY_DATE_OF_BIRTH, 0L)
        set(value) = prefs.edit().putLong(KEY_DATE_OF_BIRTH, value).apply()

    val ageYears: Int
        get() {
            if (dateOfBirthMillis == 0L) return 0
            val now = java.util.Calendar.getInstance()
            val dob = java.util.Calendar.getInstance().apply { timeInMillis = dateOfBirthMillis }
            var age = now.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
            if (now.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            return age.coerceAtLeast(0)
        }

    var weightUpdatedAt: Long
        get() = prefs.getLong(KEY_WEIGHT_UPDATED_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_WEIGHT_UPDATED_AT, value).apply()

    val hasProfile: Boolean
        get() = heightInches > 0f && weightLbs > 0f && sex.isNotEmpty() && dateOfBirthMillis > 0L

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
        private const val KEY_SEX = "sex"
        private const val KEY_DATE_OF_BIRTH = "date_of_birth"
        private const val KEY_MAX_SPEED_ALGORITHM_VERSION = "max_speed_algorithm_version"
        private const val KEY_INACTIVITY_PAUSE_MINUTES = "inactivity_pause_minutes"
        private const val KEY_INACTIVITY_EXIT_MINUTES = "inactivity_exit_minutes"
        const val WEIGHT_PROMPT_DAYS = 14
        const val DEFAULT_INACTIVITY_PAUSE_MINUTES = 1
        const val DEFAULT_INACTIVITY_EXIT_MINUTES = 2
        val INACTIVITY_PAUSE_OPTIONS = listOf(1, 2, 3, 5)
        val INACTIVITY_EXIT_OPTIONS = listOf(1, 2, 3, 5, 10, 15)
    }
}
