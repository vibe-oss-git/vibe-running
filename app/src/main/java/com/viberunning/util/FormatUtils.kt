package com.viberunning.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FormatUtils {

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
