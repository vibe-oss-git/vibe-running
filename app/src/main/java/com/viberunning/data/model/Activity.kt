package com.viberunning.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationMillis: Long = 0,
    val maxSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val caloriesBurned: Int = 0,
    val status: String = STATUS_IN_PROGRESS
) {
    companion object {
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
    }
}
