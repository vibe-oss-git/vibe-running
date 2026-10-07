package com.viberunning

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.viberunning.data.db.AppDatabase
import com.viberunning.data.repository.ActivityRepository
import com.viberunning.util.GpsStatusMonitor
import com.viberunning.util.PreferencesManager
import com.viberunning.util.SustainedSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        repository = ActivityRepository(db)
        preferencesManager = PreferencesManager(this)
        gpsStatusMonitor = GpsStatusMonitor(this)
        createNotificationChannel()
        recalculateMaxSpeedsIfNeeded()
    }

    // Recalculates saved runs' max speeds once per change to the max speed rules, so runs
    // recorded before the rules (or before a fix to them) don't keep glitch speeds.
    private fun recalculateMaxSpeedsIfNeeded() {
        if (preferencesManager.maxSpeedAlgorithmVersion >= SustainedSpeed.ALGORITHM_VERSION) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.recalculateMaxSpeeds()
                // Only after finishing, so an interrupted pass is redone next launch
                preferencesManager.maxSpeedAlgorithmVersion = SustainedSpeed.ALGORITHM_VERSION
            } catch (e: Exception) {
                Log.e("VibeRunningApp", "Max speed recalculation failed", e)
            }
        }
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
