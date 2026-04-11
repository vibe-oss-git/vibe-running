package com.viberunning.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.viberunning.MainActivity
import com.viberunning.R
import com.viberunning.VibeRunningApp
import com.viberunning.data.model.Activity
import com.viberunning.data.model.LocationPoint
import com.viberunning.util.CalorieEstimator
import com.viberunning.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocationTrackingService : Service() {

    private val binder = TrackingBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentActivityId: Long = -1
    private var lastLocation: Location? = null
    private var totalDistanceMeters = 0.0
    private var maxSpeedMps = 0.0
    private var trackingStartTime = 0L
    private var pausedDuration = 0L
    private var pauseStartTime = 0L
    private var isPaused = false
    private var isAutoPaused = false
    private var lastSaveTime = 0L
    private var lastMovementTime = 0L

    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    data class TrackingState(
        val activityId: Long = -1,
        val distanceMeters: Double = 0.0,
        val durationMillis: Long = 0,
        val currentSpeedMps: Double = 0.0,
        val maxSpeedMps: Double = 0.0,
        val avgSpeedMps: Double = 0.0,
        val isPaused: Boolean = false,
        val pointCount: Int = 0
    )

    inner class TrackingBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Skip when the user manually paused. Auto-paused still
                // processes locations so we can detect resumed movement.
                if (!isPaused || isAutoPaused) {
                    result.lastLocation?.let { processLocation(it) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1)
                if (activityId != -1L) startTracking(activityId)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
            else -> {
                // Service restarted by system (START_STICKY) — try to recover
                if (!_isTracking.value) {
                    recoverInProgressActivity()
                }
            }
        }
        return START_STICKY
    }

    private fun recoverInProgressActivity() {
        serviceScope.launch {
            val app = application as VibeRunningApp
            val activity = app.repository.getInProgressActivity() ?: run {
                stopSelf()
                return@launch
            }

            // Recover state from the saved activity row
            currentActivityId = activity.id
            totalDistanceMeters = activity.distanceMeters
            maxSpeedMps = activity.maxSpeedMps
            trackingStartTime = activity.startTime
            // Estimate paused duration from saved duration vs wall clock
            val wallElapsed = System.currentTimeMillis() - activity.startTime
            pausedDuration = if (activity.durationMillis > 0) {
                wallElapsed - activity.durationMillis
            } else 0L
            isPaused = false
            isAutoPaused = false
            lastLocation = null
            lastMovementTime = System.currentTimeMillis()
            _isTracking.value = true

            val notification = buildNotification("Resuming...", "Recovering activity")
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

            startLocationUpdates()

            // Duration ticker
            launch {
                while (_isTracking.value) {
                    updateState()
                    periodicSave()
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startTracking(activityId: Long) {
        currentActivityId = activityId
        totalDistanceMeters = 0.0
        maxSpeedMps = 0.0
        lastLocation = null
        trackingStartTime = System.currentTimeMillis()
        pausedDuration = 0L
        isPaused = false
        isAutoPaused = false
        lastSaveTime = System.currentTimeMillis()
        lastMovementTime = System.currentTimeMillis()
        _isTracking.value = true

        val notification = buildNotification("00:00", "Starting activity...")
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        startLocationUpdates()

        // Duration ticker
        serviceScope.launch {
            while (_isTracking.value) {
                updateState()
                periodicSave()
                checkInactivity()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GPS_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(GPS_FASTEST_INTERVAL_MS)
            setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun processLocation(location: Location) {
        // Filter out inaccurate readings
        if (location.accuracy > MAX_ACCURACY_METERS) return

        val speed = if (location.hasSpeed() && location.speed > 0.2f) {
            location.speed.toDouble()
        } else if (lastLocation != null) {
            val timeDelta = (location.time - lastLocation!!.time) / 1000.0
            if (timeDelta > 0) {
                val dist = lastLocation!!.distanceTo(location).toDouble()
                dist / timeDelta
            } else 0.0
        } else 0.0

        if (lastLocation != null) {
            val distance = lastLocation!!.distanceTo(location).toDouble()
            // Only count distance if speed is reasonable (< 50 m/s ~= 112 mph)
            // and distance is reasonable to filter GPS jumps
            if (distance < MAX_SINGLE_DISTANCE_METERS && speed < MAX_REASONABLE_SPEED_MPS) {
                totalDistanceMeters += distance
                // Real movement — reset the inactivity timer
                if (distance > MOVEMENT_THRESHOLD_METERS) {
                    lastMovementTime = System.currentTimeMillis()
                    // If we were auto-paused and the user started moving again,
                    // resume automatically.
                    if (isAutoPaused) {
                        isAutoPaused = false
                        resumeTracking()
                    }
                }
            }
        } else {
            // First fix counts as movement so we don't immediately auto-pause
            lastMovementTime = System.currentTimeMillis()
        }

        if (speed > maxSpeedMps && speed < MAX_REASONABLE_SPEED_MPS) {
            maxSpeedMps = speed
        }

        lastLocation = location

        val point = LocationPoint(
            activityId = currentActivityId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            speedMps = speed,
            timestamp = location.time,
            accuracy = location.accuracy
        )

        serviceScope.launch {
            val app = application as VibeRunningApp
            app.repository.addLocationPoint(point)
        }

        updateState()
    }

    private fun updateState() {
        val elapsed = if (isPaused) {
            pauseStartTime - trackingStartTime - pausedDuration
        } else {
            System.currentTimeMillis() - trackingStartTime - pausedDuration
        }

        val avgSpeed = if (elapsed > 0) {
            totalDistanceMeters / (elapsed / 1000.0)
        } else 0.0

        _trackingState.value = TrackingState(
            activityId = currentActivityId,
            distanceMeters = totalDistanceMeters,
            durationMillis = elapsed.coerceAtLeast(0),
            currentSpeedMps = lastLocation?.speed?.toDouble() ?: 0.0,
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = avgSpeed,
            isPaused = isPaused,
            pointCount = _trackingState.value.pointCount + 1
        )

        updateNotification(elapsed.coerceAtLeast(0))
    }

    private fun checkInactivity() {
        if (!_isTracking.value) return
        val idleMs = System.currentTimeMillis() - lastMovementTime
        if (!isAutoPaused && !isPaused && idleMs >= INACTIVITY_PAUSE_MS) {
            // Auto-pause after a minute of no movement and save progress
            isAutoPaused = true
            pauseTracking()
            saveCurrentState()
        } else if (isAutoPaused && idleMs >= INACTIVITY_EXIT_MS) {
            // Still idle — user likely forgot to stop. Finalize and exit.
            autoStopAndExit()
        }
    }

    private fun autoStopAndExit() {
        // Clear auto-pause so stopTracking() accounts duration correctly.
        // pausedDuration already accumulates on resume, but we're exiting —
        // just stop and let stopTracking() snapshot the current paused state.
        stopTracking()
        // Kill the process shortly after so the app fully exits as requested.
        serviceScope.launch {
            kotlinx.coroutines.delay(500)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun periodicSave() {
        val now = System.currentTimeMillis()
        if (now - lastSaveTime >= SAVE_INTERVAL_MS) {
            lastSaveTime = now
            saveCurrentState()
        }
    }

    private fun saveCurrentState() {
        if (currentActivityId == -1L) return
        val state = _trackingState.value
        serviceScope.launch {
            val app = application as VibeRunningApp
            val activity = app.repository.getActivity(currentActivityId) ?: return@launch
            app.repository.updateActivity(
                activity.copy(
                    distanceMeters = state.distanceMeters,
                    durationMillis = state.durationMillis,
                    maxSpeedMps = state.maxSpeedMps,
                    avgSpeedMps = state.avgSpeedMps
                )
            )
        }
    }

    private fun saveCurrentStateBlocking() {
        if (currentActivityId == -1L) return
        val state = _trackingState.value
        try {
            runBlocking {
                val app = application as VibeRunningApp
                val activity = app.repository.getActivity(currentActivityId) ?: return@runBlocking
                app.repository.updateActivity(
                    activity.copy(
                        distanceMeters = state.distanceMeters,
                        durationMillis = state.durationMillis,
                        maxSpeedMps = state.maxSpeedMps,
                        avgSpeedMps = state.avgSpeedMps
                    )
                )
            }
        } catch (_: Exception) {
            // Best-effort save during shutdown
        }
    }

    private fun pauseTracking() {
        isPaused = true
        pauseStartTime = System.currentTimeMillis()
        _trackingState.value = _trackingState.value.copy(isPaused = true)
    }

    private fun resumeTracking() {
        if (isPaused) {
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            isPaused = false
            _trackingState.value = _trackingState.value.copy(isPaused = false)
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false

        val elapsed = if (isPaused) {
            pauseStartTime - trackingStartTime - pausedDuration
        } else {
            System.currentTimeMillis() - trackingStartTime - pausedDuration
        }.coerceAtLeast(0)

        val avgSpeed = if (elapsed > 0) {
            totalDistanceMeters / (elapsed / 1000.0)
        } else 0.0

        serviceScope.launch {
            val app = application as VibeRunningApp
            val prefs = app.preferencesManager
            val calories = CalorieEstimator.estimate(
                avgSpeedMps = avgSpeed,
                durationMillis = elapsed,
                weightLbs = prefs.weightLbs,
                heightInches = prefs.heightInches,
                ageYears = prefs.ageYears,
                isMale = prefs.sex == "male"
            )
            val activity = app.repository.getActivity(currentActivityId)
            activity?.let {
                app.repository.updateActivity(
                    it.copy(
                        endTime = System.currentTimeMillis(),
                        distanceMeters = totalDistanceMeters,
                        durationMillis = elapsed,
                        maxSpeedMps = maxSpeedMps,
                        avgSpeedMps = avgSpeed,
                        caloriesBurned = calories,
                        status = Activity.STATUS_COMPLETED
                    )
                )
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App swiped from recents — save progress so it's not lost
        if (_isTracking.value) {
            saveCurrentStateBlocking()
        }
    }

    override fun onDestroy() {
        // Rescue save if still tracking when the OS kills us
        if (_isTracking.value) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            saveCurrentStateBlocking()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(duration: String, detail: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VibeRunningApp.TRACKING_CHANNEL_ID)
            .setContentTitle(duration)
            .setContentText(detail)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(duration)
                    .bigText(detail)
            )
            .setSmallIcon(R.drawable.ic_run)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(elapsedMillis: Long) {
        val distance = FormatUtils.formatDistance(totalDistanceMeters, useImperial = true)
        val duration = FormatUtils.formatDuration(elapsedMillis)
        val pace = FormatUtils.formatPace(
            if (elapsedMillis > 0) totalDistanceMeters / (elapsedMillis / 1000.0) else 0.0,
            useImperial = true
        )
        val speed = FormatUtils.formatSpeed(lastLocation?.speed?.toDouble() ?: 0.0, useImperial = true)
        val status = if (isPaused) " (PAUSED)" else ""
        val title = "$duration$status"
        val detail = "$distance  |  Pace: $pace  |  Speed: $speed"

        val notification = buildNotification(title, detail)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_ACTIVITY_ID = "EXTRA_ACTIVITY_ID"
        const val NOTIFICATION_ID = 1

        private const val GPS_INTERVAL_MS = 2100L
        private const val GPS_FASTEST_INTERVAL_MS = 700L
        private const val MIN_DISTANCE_METERS = 2f
        private const val MAX_ACCURACY_METERS = 30f
        private const val MAX_SINGLE_DISTANCE_METERS = 100.0
        private const val MAX_REASONABLE_SPEED_MPS = 50.0
        private const val SAVE_INTERVAL_MS = 30_000L
        private const val MOVEMENT_THRESHOLD_METERS = 3.0
        private const val INACTIVITY_PAUSE_MS = 60_000L
        private const val INACTIVITY_EXIT_MS = 120_000L
    }
}
