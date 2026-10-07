package com.viberunning.util

import com.viberunning.data.model.LocationPoint

/**
 * Max speed as the fastest speed held for at least [WINDOW_MS]: straight-line distance from
 * the start of the window to its end, divided by its duration. A position that jumps away
 * and back mid-window has no effect, and one at the window's end shifts the result only by
 * its offset spread over 5 seconds, so a glitch can't set the max speed the way a single
 * reading could. Over 5 seconds a runner's or cyclist's path is close to straight, so the
 * straight-line distance is close to the distance traveled.
 *
 * A speed only counts once two consecutive windows (one fix apart) both reach it. A single
 * bad fix can only distort the windows that start or end on it, never two in a row, so it
 * has no effect at all. Real efforts of about 6 seconds or longer are measured in full.
 *
 * Fed one GPS fix at a time. Used live by the tracking service and, through
 * [SustainedSpeed.maxSpeedOf], to recalculate saved runs, so both give the same result.
 */
class SustainedSpeedTracker(private val maxAccuracyMeters: Float = GOOD_ACCURACY_METERS) {

    private class Sample(val time: Long, val latitude: Double, val longitude: Double)

    private val window = ArrayDeque<Sample>()
    // Speed of the previous full window, if it immediately precedes the current one
    private var previousWindowSpeed: Double? = null

    /** Highest sustained speed so far, in m/s. */
    var maxSpeedMps = 0.0
        private set

    /** Whether at least one full window was measured. */
    var hasMeasurement = false
        private set

    fun add(time: Long, latitude: Double, longitude: Double, accuracy: Float) {
        if (accuracy > maxAccuracyMeters) return
        window.lastOrNull()?.let { last ->
            val dt = time - last.time
            if (dt <= 0) return
            val step = LocationUtils.distanceBetween(last.latitude, last.longitude, latitude, longitude)
            // A gap (pause, lost signal) or a jump: start a new window from here
            if (dt > MAX_GAP_MS || step >= MAX_STEP_METERS) breakWindow()
        }
        window.addLast(Sample(time, latitude, longitude))

        // Keep the newest sample that's at least a full window old as the window start
        while (window.size >= 2 && time - window[1].time >= WINDOW_MS) window.removeFirst()
        val start = window.first()
        val span = time - start.time
        if (span >= WINDOW_MS) {
            val distance = LocationUtils.distanceBetween(start.latitude, start.longitude, latitude, longitude)
            val speed = distance / (span / 1000.0)
            previousWindowSpeed?.let { previous ->
                hasMeasurement = true
                val held = minOf(previous, speed)
                if (held > maxSpeedMps) maxSpeedMps = held
            }
            previousWindowSpeed = speed
        }
    }

    /** Starts a new window, e.g. after the user resumes from a pause. Keeps the max. */
    fun breakWindow() {
        window.clear()
        previousWindowSpeed = null
    }

    companion object {
        const val WINDOW_MS = 5_000L
        // Fixes less accurate than this are skipped; glitches usually happen when accuracy drops
        const val GOOD_ACCURACY_METERS = 20f
        private const val MAX_GAP_MS = 10_000L
        private const val MAX_STEP_METERS = 100.0
    }
}

object SustainedSpeed {

    /**
     * Version of the max speed rules. Saved runs are recalculated once whenever this is
     * higher than the version they were last recalculated with.
     */
    const val ALGORITHM_VERSION = 1

    /**
     * Max sustained speed of a saved run, or null if no full window could be measured
     * (e.g. a very short run). Uses only accurate fixes when there are enough of them,
     * otherwise all fixes.
     */
    fun maxSpeedOf(points: List<LocationPoint>): Double? =
        measure(points, SustainedSpeedTracker.GOOD_ACCURACY_METERS)
            ?: measure(points, Float.MAX_VALUE)

    private fun measure(points: List<LocationPoint>, maxAccuracy: Float): Double? {
        val tracker = SustainedSpeedTracker(maxAccuracy)
        for (p in points) tracker.add(p.timestamp, p.latitude, p.longitude, p.accuracy)
        return if (tracker.hasMeasurement) tracker.maxSpeedMps else null
    }
}
