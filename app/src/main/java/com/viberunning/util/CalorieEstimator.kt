package com.viberunning.util

/**
 * Estimates calories burned during running using MET (Metabolic Equivalent of Task)
 * applied against a personalized BMR from the Mifflin-St Jeor equation.
 *
 * Mifflin-St Jeor BMR:
 *   Male:   10 × weightKg + 6.25 × heightCm - 5 × age + 5
 *   Female: 10 × weightKg + 6.25 × heightCm - 5 × age - 161
 *
 * Calories = MET × (BMR / 24) × durationHours
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
 */
object CalorieEstimator {

    /**
     * Estimate calories burned for an activity using Mifflin-St Jeor BMR.
     * @param avgSpeedMps average speed in meters per second
     * @param durationMillis duration in milliseconds
     * @param weightLbs weight in pounds
     * @param heightInches height in inches
     * @param ageYears age in years
     * @param isMale true for male, false for female
     * @return estimated calories burned
     */
    fun estimate(
        avgSpeedMps: Double,
        durationMillis: Long,
        weightLbs: Float,
        heightInches: Float,
        ageYears: Int,
        isMale: Boolean
    ): Int {
        if (avgSpeedMps <= 0.0 || durationMillis <= 0 || weightLbs <= 0f) return 0

        val speedMph = avgSpeedMps * 2.23694
        val met = speedToMet(speedMph)
        val bmrPerHour = calcBmrPerHour(weightLbs, heightInches, ageYears, isMale)
        val durationHours = durationMillis / 3_600_000.0

        return (met * bmrPerHour * durationHours).toInt()
    }

    /**
     * Estimate calories burned over a short interval (for real-time display).
     * @param currentSpeedMps current speed in meters per second
     * @param intervalMillis time interval in milliseconds
     * @param weightLbs weight in pounds
     * @param heightInches height in inches
     * @param ageYears age in years
     * @param isMale true for male, false for female
     * @return estimated calories burned in this interval
     */
    fun estimateInterval(
        currentSpeedMps: Double,
        intervalMillis: Long,
        weightLbs: Float,
        heightInches: Float,
        ageYears: Int,
        isMale: Boolean
    ): Double {
        if (currentSpeedMps <= 0.0 || intervalMillis <= 0 || weightLbs <= 0f) return 0.0

        val speedMph = currentSpeedMps * 2.23694
        val met = speedToMet(speedMph)
        val bmrPerHour = calcBmrPerHour(weightLbs, heightInches, ageYears, isMale)
        val durationHours = intervalMillis / 3_600_000.0

        return met * bmrPerHour * durationHours
    }

    /**
     * Calculate hourly BMR using Mifflin-St Jeor equation.
     * Falls back to generic 1 kcal/kg/hr if height or age is missing.
     */
    private fun calcBmrPerHour(
        weightLbs: Float,
        heightInches: Float,
        ageYears: Int,
        isMale: Boolean
    ): Double {
        val weightKg = weightLbs * 0.453592
        val heightCm = heightInches * 2.54

        if (heightCm <= 0 || ageYears <= 0) {
            // Fallback to generic MET assumption
            return weightKg
        }

        val bmrDaily = if (isMale) {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears - 161.0
        }

        return bmrDaily / 24.0
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
