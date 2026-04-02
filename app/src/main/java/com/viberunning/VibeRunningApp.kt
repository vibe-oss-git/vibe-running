package com.viberunning

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.viberunning.data.db.AppDatabase
import com.viberunning.data.repository.ActivityRepository
import com.viberunning.util.GpsStatusMonitor
import com.viberunning.util.PreferencesManager

class VibeRunningApp : Application() {

    lateinit var repository: ActivityRepository
        private set
    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var gpsStatusMonitor: GpsStatusMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = ActivityRepository(db.activityDao(), db.locationPointDao())
        preferencesManager = PreferencesManager(this)
        gpsStatusMonitor = GpsStatusMonitor(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            TRACKING_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking_channel"
    }
}
