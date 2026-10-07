package com.viberunning.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.viberunning.data.model.Activity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Insert
    suspend fun insert(activity: Activity): Long

    @Update
    suspend fun update(activity: Activity)

    // Only touches runs still in progress, so a late periodic save can't
    // overwrite the final values or status of a completed run.
    @Query(
        "UPDATE activities SET distanceMeters = :distanceMeters, durationMillis = :durationMillis, " +
            "maxSpeedMps = :maxSpeedMps, avgSpeedMps = :avgSpeedMps " +
            "WHERE id = :id AND status = 'in_progress'"
    )
    suspend fun updateProgress(
        id: Long,
        distanceMeters: Double,
        durationMillis: Long,
        maxSpeedMps: Double,
        avgSpeedMps: Double
    )

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): Activity?

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observeById(id: Long): Flow<Activity?>

    @Query("SELECT * FROM activities WHERE status = 'completed' ORDER BY startTime DESC")
    fun observeCompleted(): Flow<List<Activity>>

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Stats queries. Each record skips runs excluded from it; the numbers in
    // "excludedRecords & n" are PersonalRecord flag values.
    @Query(
        "SELECT * FROM activities WHERE status = 'completed' AND (excludedRecords & 1) = 0 " +
            "ORDER BY maxSpeedMps DESC LIMIT 1"
    )
    fun observeTopSpeed(): Flow<Activity?>

    @Query(
        "SELECT * FROM activities WHERE status = 'completed' AND (excludedRecords & 2) = 0 " +
            "ORDER BY distanceMeters DESC LIMIT 1"
    )
    fun observeLongestDistance(): Flow<Activity?>

    // Runs under a minute are excluded: a few seconds of GPS can give an unrealistic pace
    @Query(
        "SELECT * FROM activities WHERE status = 'completed' AND distanceMeters > 0 " +
            "AND durationMillis >= 60000 AND (excludedRecords & 4) = 0 " +
            "ORDER BY (durationMillis / distanceMeters) ASC LIMIT 1"
    )
    fun observeFastestPace(): Flow<Activity?>

    @Query(
        "SELECT * FROM activities WHERE status = 'completed' AND (excludedRecords & 8) = 0 " +
            "ORDER BY durationMillis DESC LIMIT 1"
    )
    fun observeLongestDuration(): Flow<Activity?>

    @Query("SELECT COUNT(*) FROM activities WHERE status = 'completed'")
    fun observeTotalActivities(): Flow<Int>

    @Query("SELECT SUM(distanceMeters) FROM activities WHERE status = 'completed'")
    fun observeTotalDistance(): Flow<Double?>

    @Query("SELECT SUM(durationMillis) FROM activities WHERE status = 'completed'")
    fun observeTotalDuration(): Flow<Long?>

    @Query("SELECT SUM(caloriesBurned) FROM activities WHERE status = 'completed'")
    fun observeTotalCalories(): Flow<Int?>

    @Query("UPDATE activities SET maxSpeedMps = :maxSpeedMps WHERE id = :id")
    suspend fun updateMaxSpeed(id: Long, maxSpeedMps: Double)

    @Query("UPDATE activities SET excludedRecords = excludedRecords | :flag WHERE id = :id")
    suspend fun excludeFromRecord(id: Long, flag: Int)

    @Query("UPDATE activities SET excludedRecords = excludedRecords | :flags WHERE startTime = :startTime")
    suspend fun excludeFromRecordsByStartTime(startTime: Long, flags: Int)

    @Query("SELECT id FROM activities WHERE status = 'completed' ORDER BY startTime ASC")
    suspend fun getCompletedIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM activities WHERE startTime = :startTime)")
    suspend fun existsWithStartTime(startTime: Long): Boolean

    @Query("SELECT * FROM activities WHERE status = 'in_progress' ORDER BY startTime DESC LIMIT 1")
    suspend fun getInProgress(): Activity?
}
