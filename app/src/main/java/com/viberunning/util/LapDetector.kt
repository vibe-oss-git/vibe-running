package com.viberunning.util

import com.viberunning.data.model.LocationPoint

data class Lap(
    val number: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val avgSpeedMps: Double,
    val startIndex: Int,
    val endIndex: Int
)

object LapDetector {

    private const val GEOFENCE_RADIUS_METERS = 30f
    private const val MIN_LAP_DISTANCE_METERS = 200.0
    private const val MIN_LAP_DURATION_MS = 60_000L

    fun detectLaps(points: List<LocationPoint>): List<Lap> {
        if (points.size < 2) return emptyList()

        val start = points.first()
        val laps = mutableListOf<Lap>()
        var lapStartIndex = 0
        var insideGeofence = true
        var lapDistance = 0.0

        for (i in 1 until points.size) {
            val segmentDist = LocationUtils.distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            lapDistance += segmentDist

            val distToStart = LocationUtils.distanceBetween(
                points[i].latitude, points[i].longitude,
                start.latitude, start.longitude
            )

            if (insideGeofence) {
                if (distToStart > GEOFENCE_RADIUS_METERS) {
                    insideGeofence = false
                }
            } else {
                if (distToStart <= GEOFENCE_RADIUS_METERS) {
                    val lapDuration = points[i].timestamp - points[lapStartIndex].timestamp
                    if (lapDistance >= MIN_LAP_DISTANCE_METERS && lapDuration >= MIN_LAP_DURATION_MS) {
                        val avgSpeed = if (lapDuration > 0) {
                            lapDistance / (lapDuration / 1000.0)
                        } else 0.0
                        laps.add(
                            Lap(
                                number = laps.size + 1,
                                distanceMeters = lapDistance,
                                durationMillis = lapDuration,
                                avgSpeedMps = avgSpeed,
                                startIndex = lapStartIndex,
                                endIndex = i
                            )
                        )
                        lapStartIndex = i
                        lapDistance = 0.0
                    }
                    insideGeofence = true
                }
            }
        }

        return laps
    }

    fun detectLapCount(
        startLat: Double,
        startLon: Double,
        currentLat: Double,
        currentLon: Double,
        totalDistance: Double,
        previousLapCount: Int,
        wasInsideGeofence: Boolean,
        lastLapDistance: Double
    ): Triple<Int, Boolean, Double> {
        val distToStart = LocationUtils.distanceBetween(
            currentLat, currentLon, startLat, startLon
        )
        val insideNow = distToStart <= GEOFENCE_RADIUS_METERS
        val distSinceLastLap = totalDistance - lastLapDistance

        if (wasInsideGeofence && !insideNow) {
            return Triple(previousLapCount, false, lastLapDistance)
        }

        if (!wasInsideGeofence && insideNow &&
            distSinceLastLap >= MIN_LAP_DISTANCE_METERS
        ) {
            return Triple(previousLapCount + 1, true, totalDistance)
        }

        return Triple(previousLapCount, insideNow, lastLapDistance)
    }
}
