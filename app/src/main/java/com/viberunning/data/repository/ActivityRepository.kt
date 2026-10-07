package com.viberunning.data.repository

import androidx.room.withTransaction
import com.viberunning.data.db.AppDatabase
import com.viberunning.data.model.Activity
import com.viberunning.data.model.LocationPoint
import com.viberunning.data.model.PersonalRecord
import com.viberunning.util.SustainedSpeed
import kotlinx.coroutines.flow.Flow

class ActivityRepository(private val database: AppDatabase) {
    private val activityDao = database.activityDao()
    private val locationPointDao = database.locationPointDao()

    // Activity operations
    suspend fun createActivity(): Long = activityDao.insert(Activity())

    suspend fun getActivity(id: Long): Activity? = activityDao.getById(id)

    fun observeActivity(id: Long): Flow<Activity?> = activityDao.observeById(id)

    suspend fun updateActivity(activity: Activity) = activityDao.update(activity)

    suspend fun updateProgress(
        id: Long,
        distanceMeters: Double,
        durationMillis: Long,
        maxSpeedMps: Double,
        avgSpeedMps: Double
    ) = activityDao.updateProgress(id, distanceMeters, durationMillis, maxSpeedMps, avgSpeedMps)

    suspend fun deleteActivity(id: Long) = activityDao.deleteById(id)

    /** Permanently stops a run from counting toward one personal record. */
    suspend fun disregardRecord(activityId: Long, record: PersonalRecord) =
        activityDao.excludeFromRecord(activityId, record.flag)

    fun observeCompletedActivities(): Flow<List<Activity>> = activityDao.observeCompleted()

    suspend fun getCompletedActivityIds(): List<Long> = activityDao.getCompletedIds()

    /**
     * Recalculates every completed run's max speed from its GPS track with the current
     * [SustainedSpeed] rules. Runs too short to measure keep their value. One run is loaded
     * at a time, and the work can be safely repeated if interrupted.
     */
    suspend fun recalculateMaxSpeeds() {
        for (id in activityDao.getCompletedIds()) {
            val maxSpeed = SustainedSpeed.maxSpeedOf(locationPointDao.getByActivityId(id)) ?: continue
            activityDao.updateMaxSpeed(id, maxSpeed)
        }
    }

    /**
     * Adds an activity from a backup with its points. Returns false, adding nothing, when an
     * activity with the same start time already exists (it's treated as a duplicate). Records
     * disregarded in the backup are still disregarded on the existing activity.
     */
    suspend fun importActivity(activity: Activity, points: List<LocationPoint>): Boolean =
        database.withTransaction {
            if (activityDao.existsWithStartTime(activity.startTime)) {
                if (activity.excludedRecords != 0) {
                    activityDao.excludeFromRecordsByStartTime(activity.startTime, activity.excludedRecords)
                }
                return@withTransaction false
            }
            val id = activityDao.insert(activity.copy(id = 0))
            locationPointDao.insertAll(points.map { it.copy(id = 0, activityId = id) })
            true
        }

    // Location point operations
    suspend fun addLocationPoint(point: LocationPoint) = locationPointDao.insert(point)

    suspend fun getLocationPoints(activityId: Long): List<LocationPoint> =
        locationPointDao.getByActivityId(activityId)

    suspend fun getLastLocationTime(activityId: Long): Long? =
        locationPointDao.getLastTimestamp(activityId)

    fun observeLocationPoints(activityId: Long): Flow<List<LocationPoint>> =
        locationPointDao.observeByActivityId(activityId)

    suspend fun getInProgressActivity(): Activity? = activityDao.getInProgress()

    // Stats
    fun observeTopSpeed(): Flow<Activity?> = activityDao.observeTopSpeed()
    fun observeLongestDistance(): Flow<Activity?> = activityDao.observeLongestDistance()
    fun observeFastestPace(): Flow<Activity?> = activityDao.observeFastestPace()
    fun observeLongestDuration(): Flow<Activity?> = activityDao.observeLongestDuration()
    fun observeTotalActivities(): Flow<Int> = activityDao.observeTotalActivities()
    fun observeTotalDistance(): Flow<Double?> = activityDao.observeTotalDistance()
    fun observeTotalDuration(): Flow<Long?> = activityDao.observeTotalDuration()
    fun observeTotalCalories(): Flow<Int?> = activityDao.observeTotalCalories()
}
