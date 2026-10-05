package com.viberunning.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.viberunning.data.model.LocationPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Insert
    suspend fun insert(point: LocationPoint)

    @Query("SELECT * FROM location_points WHERE activityId = :activityId ORDER BY timestamp ASC")
    fun observeByActivityId(activityId: Long): Flow<List<LocationPoint>>

    @Query("SELECT MAX(timestamp) FROM location_points WHERE activityId = :activityId")
    suspend fun getLastTimestamp(activityId: Long): Long?
}
