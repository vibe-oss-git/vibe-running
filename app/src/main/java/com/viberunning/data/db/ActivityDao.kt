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

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): Activity?

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observeById(id: Long): Flow<Activity?>

    @Query("SELECT * FROM activities WHERE status = 'completed' ORDER BY startTime DESC")
    fun observeCompleted(): Flow<List<Activity>>

    @Query("SELECT * FROM activities WHERE status = 'completed' ORDER BY startTime DESC")
    suspend fun getCompleted(): List<Activity>

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Stats queries
    @Query("SELECT MAX(maxSpeedMps) FROM activities WHERE status = 'completed'")
    fun observeTopSpeed(): Flow<Double?>

    @Query("SELECT MAX(distanceMeters) FROM activities WHERE status = 'completed'")
    fun observeLongestDistance(): Flow<Double?>

    @Query("SELECT * FROM activities WHERE status = 'completed' AND distanceMeters > 0 ORDER BY (durationMillis / distanceMeters) ASC LIMIT 1")
    fun observeFastestPace(): Flow<Activity?>

    @Query("SELECT * FROM activities WHERE status = 'completed' ORDER BY durationMillis DESC LIMIT 1")
    fun observeLongestDuration(): Flow<Activity?>

    @Query("SELECT COUNT(*) FROM activities WHERE status = 'completed'")
    fun observeTotalActivities(): Flow<Int>

    @Query("SELECT SUM(distanceMeters) FROM activities WHERE status = 'completed'")
    fun observeTotalDistance(): Flow<Double?>

    @Query("SELECT SUM(durationMillis) FROM activities WHERE status = 'completed'")
    fun observeTotalDuration(): Flow<Long?>
}
