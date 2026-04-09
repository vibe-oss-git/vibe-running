package com.viberunning.util

/**
 * Estimates calories burned during running using MET (Metabolic Equivalent of Task).
 *
 * MET values based on the Compendium of Physical Activities (Ainsworth et al.):
 * - Walking 2.0 mph: 2.8 MET
 * - Walking 3.0 mph: 3.5 MET
 * - Jogging: 7.0 MET
 * - Running 5.0 mph: 8.3 MET
 * - Running 6.0 mph: 9.8 MET
 * - Running 7.0 mph: 11.0 MET
 * - Running 8.0 mph: 11.8 MET
 * - Running 9.0 mph: 12.8 MET
 * - Running 10.0 mph: 14.5 MET
 *
 * Formula: Calories = MET × weightKg × durationHours
 */
object CalorieEstimator {

    /**
     * Estimate calories burned for an activity.
     * @param avgSpeedMps average speed in meters per second
     * @param durationMillis duration in milliseconds
     * @param weightLbs weight in pounds
     * @return estimated calories burned
     */
    fun estimate(avgSpeedMps: Double, durationMillis: Long, weightLbs: Float): Int {
        if (avgSpeedMps <= 0.0 || durationMillis <= 0 || weightLbs <= 0f) return 0

        val speedMph = avgSpeedMps * 2.23694
        val met = speedToMet(speedMph)
        val weightKg = weightLbs * 0.453592
        val durationHours = durationMillis / 3_600_000.0

        return (met * weightKg * durationHours).toInt()
    }

    /**
     * Estimate calories burned over a short interval (for real-time display).
     * @param currentSpeedMps current speed in meters per second
     * @param intervalMillis time interval in milliseconds
     * @param weightLbs weight in pounds
     * @return estimated calories burned in this interval
     */
    fun estimateInterval(currentSpeedMps: Double, intervalMillis: Long, weightLbs: Float): Double {
        if (currentSpeedMps <= 0.0 || intervalMillis <= 0 || weightLbs <= 0f) return 0.0

        val speedMph = currentSpeedMps * 2.23694
        val met = speedToMet(speedMph)
        val weightKg = weightLbs * 0.453592
        val durationHours = intervalMillis / 3_600_000.0

        return met * weightKg * durationHours
    }

    /**
     * Interpolate MET value from speed in mph using the Compendium data points.
     */
    private fun speedToMet(speedMph: Double): Double {
        // Data points: (speed mph, MET)
        val metTable = listOf(
            2.0 to 2.8,
            3.0 to 3.5,
            3.5 to 4.3,
            4.0 to 5.0,
            4.5 to 7.0,
            5.0 to 8.3,
            5.5 to 9.0,
            6.0 to 9.8,
            6.5 to 10.5,
            7.0 to 11.0,
            7.5 to 11.5,
            8.0 to 11.8,
            8.5 to 12.3,
            9.0 to 12.8,
            9.5 to 13.5,
            10.0 to 14.5,
            11.0 to 16.0,
            12.0 to 19.0
        )

        if (speedMph <= metTable.first().first) return metTable.first().second
        if (speedMph >= metTable.last().first) return metTable.last().second

        // Linear interpolation between adjacent data points
        for (i in 0 until metTable.size - 1) {
            val (s1, m1) = metTable[i]
            val (s2, m2) = metTable[i + 1]
            if (speedMph in s1..s2) {
                val fraction = (speedMph - s1) / (s2 - s1)
                return m1 + (m2 - m1) * fraction
            }
        }

        return metTable.last().second
    }
}
