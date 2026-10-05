package com.viberunning.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FormatUtils {

    private const val KG_PER_LB = 0.453592

    fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDistance(meters: Double, useImperial: Boolean): String {
        return if (useImperial) {
            val miles = meters / 1609.344
            String.format(Locale.US, "%.2f mi", miles)
        } else {
            if (meters < 1000) {
                String.format(Locale.US, "%.0f m", meters)
            } else {
                String.format(Locale.US, "%.2f km", meters / 1000.0)
            }
        }
    }

    fun formatSpeed(metersPerSecond: Double, useImperial: Boolean): String {
        return if (useImperial) {
            val mph = metersPerSecond * 2.23694
            String.format(Locale.US, "%.1f mph", mph)
        } else {
            val kmh = metersPerSecond * 3.6
            String.format(Locale.US, "%.1f km/h", kmh)
        }
    }

    fun formatPace(metersPerSecond: Double, useImperial: Boolean): String {
        if (metersPerSecond <= 0.0) return "--:--"
        val secondsPerMeter = 1.0 / metersPerSecond
        val secondsPerUnit = if (useImperial) {
            secondsPerMeter * 1609.344 // seconds per mile
        } else {
            secondsPerMeter * 1000.0 // seconds per km
        }
        val minutes = (secondsPerUnit / 60).toInt()
        val seconds = (secondsPerUnit % 60).toInt()
        val unit = if (useImperial) "mi" else "km"
        return String.format(Locale.US, "%d:%02d /%s", minutes, seconds, unit)
    }

    // Formats elevation values — converts meters to feet for imperial
    fun formatElevation(meters: Double, useImperial: Boolean): String {
        return if (useImperial) {
            val feet = meters * 3.28084
            String.format(Locale.US, "%.0f ft", feet)
        } else {
            String.format(Locale.US, "%.0f m", meters)
        }
    }

    // Weight is stored in pounds and shown/entered with one decimal in the display unit.
    fun weightInputText(lbs: Float, useImperial: Boolean): String {
        if (lbs <= 0f) return ""
        val value = if (useImperial) lbs.toDouble() else lbs * KG_PER_LB
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    fun formatWeight(lbs: Float, useImperial: Boolean): String =
        "${weightInputText(lbs, useImperial)} ${if (useImperial) "lbs" else "kg"}"

    // Keeps digits and one decimal point, with at most one digit after it
    fun filterWeightInput(text: String): String {
        val digitsAndDot = text.filter { it.isDigit() || it == '.' }
        val dot = digitsAndDot.indexOf('.')
        val cleaned = if (dot == -1) {
            digitsAndDot
        } else {
            digitsAndDot.substring(0, dot + 1) +
                digitsAndDot.substring(dot + 1).replace(".", "").take(1)
        }
        return cleaned.take(6)
    }

    // Converts an entered weight (display unit) to pounds; null if not a positive number
    fun parseWeightToLbs(text: String, useImperial: Boolean): Float? {
        val value = text.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val rounded = Math.round(value * 10) / 10.0
        return (if (useImperial) rounded else rounded / KG_PER_LB).toFloat()
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDayOfWeek(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
