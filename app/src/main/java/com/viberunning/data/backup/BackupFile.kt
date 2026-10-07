package com.viberunning.data.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.viberunning.data.model.Activity
import com.viberunning.data.model.LocationPoint
import com.viberunning.data.model.PersonalRecord
import com.viberunning.data.repository.ActivityRepository
import com.viberunning.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

enum class BackupSection(val key: String, val label: String) {
    PROFILE("profile", "Profile data"),
    SETTINGS("settings", "Settings"),
    ACTIVITY("activities", "Activity");

    companion object {
        fun fromKey(key: String): BackupSection? = entries.firstOrNull { it.key == key }
    }
}

/** What a backup file contains, read from its header without reading the data. */
data class BackupInfo(
    val sections: Set<BackupSection>,
    val activityCount: Int,
    val exportedAt: Long
)

data class ImportResult(
    val importedActivities: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int
)

class BackupFormatException(message: String) : Exception(message)

/**
 * Reads and writes backup files: JSON, streamed so a long history never has to fit in
 * memory at once. The header (format, version, sections, activity count) comes first so an
 * import can show what a file contains before reading the data.
 *
 * Activities are merged on import: one whose start time matches an existing activity is a
 * duplicate and skipped. Profile data and settings replace the current values.
 */
class BackupFile(
    private val repository: ActivityRepository,
    private val prefs: PreferencesManager
) {

    /** Writes the chosen sections. Returns the number of activities written. */
    suspend fun export(output: OutputStream, sections: Set<BackupSection>): Int =
        withContext(Dispatchers.IO) {
            val ids = if (BackupSection.ACTIVITY in sections) {
                repository.getCompletedActivityIds()
            } else {
                emptyList()
            }
            var written = 0
            JsonWriter(output.bufferedWriter()).use { w ->
                w.beginObject()
                w.name(KEY_FORMAT).value(FORMAT)
                w.name(KEY_FORMAT_VERSION).value(FORMAT_VERSION.toLong())
                w.name(KEY_EXPORTED_AT).value(System.currentTimeMillis())
                w.name(KEY_SECTIONS).beginArray()
                BackupSection.entries.filter { it in sections }.forEach { w.value(it.key) }
                w.endArray()
                w.name(KEY_ACTIVITY_COUNT).value(ids.size.toLong())

                if (BackupSection.PROFILE in sections) writeProfile(w)
                if (BackupSection.SETTINGS in sections) writeSettings(w)
                if (BackupSection.ACTIVITY in sections) {
                    w.name(BackupSection.ACTIVITY.key).beginArray()
                    for (id in ids) {
                        val activity = repository.getActivity(id) ?: continue
                        writeActivity(w, activity, repository.getLocationPoints(id))
                        written++
                    }
                    w.endArray()
                }
                w.endObject()
            }
            written
        }

    /** Reads only the header. Throws [BackupFormatException] if this isn't a backup file. */
    fun readInfo(input: InputStream): BackupInfo = JsonReader(input.bufferedReader()).use { r ->
        var formatSeen = false
        var sections = emptySet<BackupSection>()
        var activityCount = 0
        var exportedAt = 0L
        try {
            r.beginObject()
            loop@ while (r.hasNext()) {
                when (r.nextName()) {
                    KEY_FORMAT -> {
                        checkFormat(r.nextString())
                        formatSeen = true
                    }
                    KEY_FORMAT_VERSION -> checkFormatVersion(r.nextInt())
                    KEY_EXPORTED_AT -> exportedAt = r.nextLong()
                    KEY_ACTIVITY_COUNT -> activityCount = r.nextInt()
                    KEY_SECTIONS -> {
                        val found = mutableSetOf<BackupSection>()
                        r.beginArray()
                        while (r.hasNext()) BackupSection.fromKey(r.nextString())?.let { found += it }
                        r.endArray()
                        sections = found
                    }
                    // Data follows the header; nothing more is needed
                    else -> break@loop
                }
            }
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException(NOT_A_BACKUP)
        }
        if (!formatSeen) throw BackupFormatException(NOT_A_BACKUP)
        BackupInfo(sections, activityCount, exportedAt)
    }

    /**
     * Imports the chosen sections. Each activity is added in its own transaction, so if the
     * file turns out to be damaged partway, activities read before that point are kept;
     * the exception says how many.
     */
    suspend fun import(input: InputStream, sections: Set<BackupSection>): ImportResult =
        withContext(Dispatchers.IO) {
            var imported = 0
            var duplicates = 0
            var invalid = 0
            JsonReader(input.bufferedReader()).use { r ->
                try {
                    var formatSeen = false
                    r.beginObject()
                    while (r.hasNext()) {
                        val name = r.nextName()
                        val section = BackupSection.fromKey(name)
                        if (section != null && !formatSeen) throw BackupFormatException(NOT_A_BACKUP)
                        when {
                            name == KEY_FORMAT -> {
                                checkFormat(r.nextString())
                                formatSeen = true
                            }
                            name == KEY_FORMAT_VERSION -> checkFormatVersion(r.nextInt())
                            section == null || section !in sections -> r.skipValue()
                            section == BackupSection.PROFILE -> readProfile(r)
                            section == BackupSection.SETTINGS -> readSettings(r)
                            section == BackupSection.ACTIVITY -> {
                                r.beginArray()
                                while (r.hasNext()) {
                                    val (activity, points) = readActivity(r)
                                    when {
                                        activity == null -> invalid++
                                        repository.importActivity(activity, points) -> imported++
                                        else -> duplicates++
                                    }
                                }
                                r.endArray()
                            }
                        }
                    }
                    r.endObject()
                    if (!formatSeen) throw BackupFormatException(NOT_A_BACKUP)
                } catch (e: BackupFormatException) {
                    throw e
                } catch (e: Exception) {
                    throw BackupFormatException(
                        "The file is damaged or incomplete. " +
                            "$imported ${if (imported == 1) "run was" else "runs were"} " +
                            "imported before the problem was found."
                    )
                }
            }
            ImportResult(imported, duplicates, invalid)
        }

    private fun checkFormat(format: String) {
        if (format != FORMAT) throw BackupFormatException(NOT_A_BACKUP)
    }

    private fun checkFormatVersion(version: Int) {
        if (version > FORMAT_VERSION) {
            throw BackupFormatException(
                "This backup was made by a newer version of Vibe Running. Update the app to import it."
            )
        }
    }

    // --- Profile -----------------------------------------------------------------

    private fun writeProfile(w: JsonWriter) {
        w.name(BackupSection.PROFILE.key).beginObject()
        w.name("heightInches").value(prefs.heightInches.toDouble())
        w.name("weightLbs").value(prefs.weightLbs.toDouble())
        w.name("weightUpdatedAt").value(prefs.weightUpdatedAt)
        w.name("sex").value(prefs.sex)
        w.name("dateOfBirthMillis").value(prefs.dateOfBirthMillis)
        w.endObject()
    }

    // Values that are missing or out of range are left as they are on this phone
    private fun readProfile(r: JsonReader) {
        var height: Double? = null
        var weight: Double? = null
        var weightUpdatedAt: Long? = null
        var sex: String? = null
        var dob: Long? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "heightInches" -> height = r.nextDouble()
                "weightLbs" -> weight = r.nextDouble()
                "weightUpdatedAt" -> weightUpdatedAt = r.nextLong()
                "sex" -> sex = r.nextString()
                "dateOfBirthMillis" -> dob = r.nextLong()
                else -> r.skipValue()
            }
        }
        r.endObject()

        height?.takeIf { it > 0 && it < MAX_HEIGHT_INCHES }?.let { prefs.heightInches = it.toFloat() }
        weight?.takeIf { it > 0 && it < MAX_WEIGHT_LBS }?.let {
            prefs.restoreWeight(it.toFloat(), weightUpdatedAt ?: System.currentTimeMillis())
        }
        sex?.takeIf { it == "male" || it == "female" }?.let { prefs.sex = it }
        dob?.takeIf { it > 0 && it < System.currentTimeMillis() }?.let { prefs.dateOfBirthMillis = it }
    }

    // --- Settings ----------------------------------------------------------------

    private fun writeSettings(w: JsonWriter) {
        w.name(BackupSection.SETTINGS.key).beginObject()
        w.name("useImperial").value(prefs.useImperial)
        w.name("inactivityPauseMinutes").value(prefs.inactivityPauseMinutes.toLong())
        w.name("inactivityExitMinutes").value(prefs.inactivityExitMinutes.toLong())
        w.endObject()
    }

    // Timer values must be one of the choices offered in Settings, with end >= pause
    private fun readSettings(r: JsonReader) {
        var useImperial: Boolean? = null
        var pause: Int? = null
        var exit: Int? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "useImperial" -> useImperial = r.nextBoolean()
                "inactivityPauseMinutes" -> pause = r.nextInt()
                "inactivityExitMinutes" -> exit = r.nextInt()
                else -> r.skipValue()
            }
        }
        r.endObject()

        useImperial?.let { prefs.useImperial = it }
        pause?.takeIf { it in PreferencesManager.INACTIVITY_PAUSE_OPTIONS }?.let {
            prefs.inactivityPauseMinutes = it
        }
        val newPause = prefs.inactivityPauseMinutes
        val newExit = exit?.takeIf { it in PreferencesManager.INACTIVITY_EXIT_OPTIONS && it >= newPause }
        prefs.inactivityExitMinutes = newExit ?: maxOf(prefs.inactivityExitMinutes, newPause)
    }

    // --- Activities --------------------------------------------------------------

    private fun writeActivity(w: JsonWriter, activity: Activity, points: List<LocationPoint>) {
        w.beginObject()
        w.name("startTime").value(activity.startTime)
        activity.endTime?.let { w.name("endTime").value(it) }
        w.name("distanceMeters").value(finite(activity.distanceMeters))
        w.name("durationMillis").value(activity.durationMillis)
        w.name("maxSpeedMps").value(finite(activity.maxSpeedMps))
        w.name("avgSpeedMps").value(finite(activity.avgSpeedMps))
        w.name("caloriesBurned").value(activity.caloriesBurned.toLong())
        w.name("excludedRecords").value(activity.excludedRecords.toLong())
        // Each point: [latitude, longitude, altitude, speedMps, timestamp, accuracy]
        w.name("points").beginArray()
        for (p in points) {
            w.beginArray()
            w.value(finite(p.latitude))
            w.value(finite(p.longitude))
            w.value(finite(p.altitude))
            w.value(finite(p.speedMps))
            w.value(p.timestamp)
            w.value(finite(p.accuracy.toDouble()))
            w.endArray()
        }
        w.endArray()
        w.endObject()
    }

    /** Returns null for the activity if it's invalid; its points are read either way. */
    private fun readActivity(r: JsonReader): Pair<Activity?, List<LocationPoint>> {
        var startTime = 0L
        var endTime: Long? = null
        var distance = 0.0
        var duration = 0L
        var maxSpeed = 0.0
        var avgSpeed = 0.0
        var calories = 0
        var excludedRecords = 0
        val points = mutableListOf<LocationPoint>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "startTime" -> startTime = r.nextLong()
                "endTime" -> endTime = if (r.peek() == JsonToken.NULL) {
                    r.nextNull()
                    null
                } else {
                    r.nextLong()
                }
                "distanceMeters" -> distance = r.nextDouble()
                "durationMillis" -> duration = r.nextLong()
                "maxSpeedMps" -> maxSpeed = r.nextDouble()
                "avgSpeedMps" -> avgSpeed = r.nextDouble()
                "caloriesBurned" -> calories = r.nextInt()
                "excludedRecords" -> excludedRecords = r.nextInt()
                "points" -> {
                    r.beginArray()
                    while (r.hasNext()) readPoint(r)?.let { points += it }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()

        val valid = startTime > 0 && duration >= 0 &&
            listOf(distance, maxSpeed, avgSpeed).all { it.isFinite() && it >= 0 }
        if (!valid) return null to emptyList()
        val activity = Activity(
            startTime = startTime,
            endTime = endTime,
            distanceMeters = distance,
            durationMillis = duration,
            maxSpeedMps = maxSpeed,
            avgSpeedMps = avgSpeed,
            caloriesBurned = calories.coerceAtLeast(0),
            // Unknown flags are dropped
            excludedRecords = excludedRecords and PersonalRecord.ALL_FLAGS,
            status = Activity.STATUS_COMPLETED
        )
        return activity to points
    }

    // Points with impossible coordinates are dropped; activityId is set on insert
    private fun readPoint(r: JsonReader): LocationPoint? {
        r.beginArray()
        val lat = r.nextDouble()
        val lon = r.nextDouble()
        val alt = r.nextDouble()
        val speed = r.nextDouble()
        val time = r.nextLong()
        val accuracy = r.nextDouble()
        while (r.hasNext()) r.skipValue()
        r.endArray()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (!alt.isFinite() || !speed.isFinite() || !accuracy.isFinite()) return null
        return LocationPoint(
            activityId = 0,
            latitude = lat,
            longitude = lon,
            altitude = alt,
            speedMps = speed,
            timestamp = time,
            accuracy = accuracy.toFloat()
        )
    }

    // JsonWriter rejects NaN and infinity
    private fun finite(value: Double): Double = if (value.isFinite()) value else 0.0

    companion object {
        const val MIME_TYPE = "application/json"
        private const val FORMAT = "vibe-running-backup"
        private const val FORMAT_VERSION = 1
        private const val KEY_FORMAT = "format"
        private const val KEY_FORMAT_VERSION = "formatVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_SECTIONS = "sections"
        private const val KEY_ACTIVITY_COUNT = "activityCount"
        private const val MAX_HEIGHT_INCHES = 120.0
        private const val MAX_WEIGHT_LBS = 1500.0
        private const val NOT_A_BACKUP = "This file isn't a Vibe Running backup."
    }
}
