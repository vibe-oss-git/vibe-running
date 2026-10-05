package com.viberunning.data.repository

import com.viberunning.data.db.ActivityDao
import com.viberunning.data.db.LocationPointDao
import com.viberunning.data.model.Activity
import com.viberunning.data.model.LocationPoint
import kotlinx.coroutines.flow.Flow

class ActivityRepository(
    private val activityDao: ActivityDao,
    private val locationPointDao: LocationPointDao
) {
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

    fun observeCompletedActivities(): Flow<List<Activity>> = activityDao.observeCompleted()

    suspend fun getCompletedActivities(): List<Activity> = activityDao.getCompleted()

    // Location point operations
    suspend fun addLocationPoint(point: LocationPoint) = locationPointDao.insert(point)

    suspend fun getLocationPoints(activityId: Long): List<LocationPoint> =
        locationPointDao.getByActivityId(activityId)

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
