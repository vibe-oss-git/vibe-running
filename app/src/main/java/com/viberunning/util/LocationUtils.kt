package com.viberunning.util

import android.location.Location

object LocationUtils {

    fun distanceBetween(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun calculateTotalDistance(
        points: List<Pair<Double, Double>>
    ): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceBetween(
                points[i - 1].first, points[i - 1].second,
                points[i].first, points[i].second
            )
        }
        return total
    }
}
