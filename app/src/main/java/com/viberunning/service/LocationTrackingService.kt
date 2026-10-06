package com.viberunning.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
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
import com.viberunning.util.LapDetector
import com.viberunning.util.PreferencesManager
import kotlinx.coroutines.CoroutineExceptionHandler
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
    // Main dispatcher: tracking state is only touched from the main thread, the same
    // thread location callbacks arrive on. Room's suspend functions do their own I/O.
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Tracking coroutine failed", e) }
    )

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
    // Last position where movement was detected; inactivity is measured from here
    private var movementAnchor: Location? = null
    private var inactivityPauseMs = PreferencesManager.DEFAULT_INACTIVITY_PAUSE_MINUTES * 60_000L
    private var inactivityExitMs = PreferencesManager.DEFAULT_INACTIVITY_EXIT_MINUTES * 60_000L

    private var startLatitude = 0.0
    private var startLongitude = 0.0
    private var lapCount = 0
    private var wasInsideGeofence = true
    private var lastLapDistance = 0.0

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
        val lapCount: Int = 0,
        val currentLapDistanceMeters: Double = 0.0
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
                if (_isTracking.value) {
                    // Duplicate start (e.g. double tap). Keep the current run, but still
                    // satisfy startForegroundService()'s requirement to call startForeground().
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Tracking", "Activity in progress"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else if (activityId != -1L) {
                    startTracking(activityId)
                }
            }
            ACTION_PAUSE -> {
                if (_isTracking.value) {
                    isAutoPaused = false
                    pauseTracking()
                }
            }
            ACTION_RESUME -> {
                if (_isTracking.value) {
                    isAutoPaused = false
                    resumeTracking()
                    // Don't count distance covered while paused
                    lastLocation = null
                    movementAnchor = null
                    lastMovementTime = System.currentTimeMillis()
                }
            }
            ACTION_STOP -> stopTracking()
            ACTION_DISCARD -> stopTracking(
                discardActivityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, currentActivityId)
            )
            else -> {
                // Service restarted by system (START_STICKY) — try to recover
                if (!_isTracking.value) {
                    isActive = true
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
                isActive = false
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
            movementAnchor = null
            lastMovementTime = System.currentTimeMillis()
            loadInactivitySettings()
            _isTracking.value = true

            val notification = buildNotification("Resuming...", "Recovering activity")
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

            startLocationUpdates()

            // Duration ticker
            launch {
                while (_isTracking.value) {
                    updateState()
                    periodicSave()
                    checkInactivity()
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startTracking(activityId: Long) {
        isActive = true
        currentActivityId = activityId
        totalDistanceMeters = 0.0
        maxSpeedMps = 0.0
        lastLocation = null
        movementAnchor = null
        trackingStartTime = System.currentTimeMillis()
        pausedDuration = 0L
        isPaused = false
        isAutoPaused = false
        lastSaveTime = System.currentTimeMillis()
        lastMovementTime = System.currentTimeMillis()
        loadInactivitySettings()
        startLatitude = 0.0
        startLongitude = 0.0
        lapCount = 0
        wasInsideGeofence = true
        lastLapDistance = 0.0
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

        val now = System.currentTimeMillis()
        val previous = lastLocation
        val distance = previous?.distanceTo(location)?.toDouble() ?: 0.0

        val speed = if (location.hasSpeed() && location.speed > 0.2f) {
            location.speed.toDouble()
        } else if (previous != null) {
            val timeDelta = (location.time - previous.time) / 1000.0
            if (timeDelta > 0) distance / timeDelta else 0.0
        } else 0.0

        // Reject GPS jumps: unreasonable speed (>= 50 m/s ~= 112 mph) or a single
        // step that's too long. Rejected steps add no distance and don't set max speed.
        val plausible = speed < MAX_REASONABLE_SPEED_MPS && distance < MAX_SINGLE_DISTANCE_METERS

        // Movement is measured from an anchor rather than step to step. At 1s
        // sampling a runner covers only a few meters per fix, so a per-step
        // threshold would miss slow running and walking.
        val anchor = movementAnchor
        if (anchor == null) {
            movementAnchor = location
            lastMovementTime = now
        } else if (plausible && anchor.distanceTo(location) > MOVEMENT_THRESHOLD_METERS) {
            // Real movement — reset the inactivity timer
            movementAnchor = location
            lastMovementTime = now
            // If we were auto-paused and the user started moving again,
            // resume automatically.
            if (isAutoPaused) {
                isAutoPaused = false
                resumeTracking()
            }
        }

        lastLocation = location

        // While auto-paused, GPS jitter around a stationary position must not
        // add distance or points.
        if (isAutoPaused) return

        if (previous == null) {
            // First fix — record start position for lap detection
            if (startLatitude == 0.0) {
                startLatitude = location.latitude
                startLongitude = location.longitude
            }
        } else if (plausible) {
            totalDistanceMeters += distance
        }

        if (plausible && speed > maxSpeedMps) {
            maxSpeedMps = speed
        }

        if (startLatitude != 0.0) {
            val (newLapCount, insideNow, newLastLapDist) = LapDetector.detectLapCount(
                startLat = startLatitude,
                startLon = startLongitude,
                currentLat = location.latitude,
                currentLon = location.longitude,
                totalDistance = totalDistanceMeters,
                previousLapCount = lapCount,
                wasInsideGeofence = wasInsideGeofence,
                lastLapDistance = lastLapDistance
            )
            lapCount = newLapCount
            wasInsideGeofence = insideNow
            lastLapDistance = newLastLapDist
        }

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
            lapCount = lapCount,
            currentLapDistanceMeters = totalDistanceMeters - lastLapDistance
        )

        updateNotification(elapsed.coerceAtLeast(0))
    }

    // Read once per run; changes in Settings apply to the next run.
    private fun loadInactivitySettings() {
        val prefs = (application as VibeRunningApp).preferencesManager
        inactivityPauseMs = prefs.inactivityPauseMinutes * 60_000L
        inactivityExitMs = prefs.inactivityExitMinutes * 60_000L
    }

    private fun checkInactivity() {
        if (!_isTracking.value) return
        val idleMs = System.currentTimeMillis() - lastMovementTime
        if (!isAutoPaused && !isPaused && idleMs >= inactivityPauseMs) {
            // Auto-pause after the configured time with no movement and save progress
            isAutoPaused = true
            pauseTracking()
            saveCurrentState()
        } else if (isAutoPaused && idleMs >= inactivityExitMs) {
            // Still idle — user likely forgot to stop. Finalize and exit.
            autoStopAndExit()
        } else if (isPaused && !isAutoPaused &&
            System.currentTimeMillis() - pauseStartTime >= MANUAL_PAUSE_LIMIT_MS
        ) {
            // Paused by the user and never resumed — save the run. The app stays open.
            stopTracking()
        }
    }

    private fun autoStopAndExit() {
        // stopTracking() snapshots the duration as of the auto-pause.
        // Kill the process once the run is saved so the app fully exits. Posted to a
        // Handler rather than serviceScope, which onDestroy() cancels.
        stopTracking {
            Handler(Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 500)
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
        val activityId = currentActivityId
        serviceScope.launch {
            val app = application as VibeRunningApp
            app.repository.updateProgress(
                activityId,
                state.distanceMeters,
                state.durationMillis,
                state.maxSpeedMps,
                state.avgSpeedMps
            )
        }
    }

    private fun saveCurrentStateBlocking() {
        if (currentActivityId == -1L) return
        val state = _trackingState.value
        try {
            runBlocking {
                val app = application as VibeRunningApp
                app.repository.updateProgress(
                    currentActivityId,
                    state.distanceMeters,
                    state.durationMillis,
                    state.maxSpeedMps,
                    state.avgSpeedMps
                )
            }
        } catch (_: Exception) {
            // Best-effort save during shutdown
        }
    }

    private fun pauseTracking() {
        if (isPaused) return
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

    /**
     * Ends the run. The final save (or the delete, for a discarded run) finishes
     * before the service stops itself, so onDestroy() cancelling serviceScope
     * can't cut it short. [onFinished] runs after that.
     */
    private fun stopTracking(discardActivityId: Long = -1L, onFinished: () -> Unit = {}) {
        val wasTracking = _isTracking.value
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
        isActive = false

        val activityId = currentActivityId
        val elapsed = if (isPaused) {
            pauseStartTime - trackingStartTime - pausedDuration
        } else {
            System.currentTimeMillis() - trackingStartTime - pausedDuration
        }.coerceAtLeast(0)

        val avgSpeed = if (elapsed > 0) {
            totalDistanceMeters / (elapsed / 1000.0)
        } else 0.0
        val distance = totalDistanceMeters
        val maxSpeed = maxSpeedMps

        serviceScope.launch {
            val app = application as VibeRunningApp
            try {
                if (discardActivityId != -1L) {
                    app.repository.deleteActivity(discardActivityId)
                } else if (wasTracking && activityId != -1L) {
                    val prefs = app.preferencesManager
                    val calories = CalorieEstimator.estimate(
                        avgSpeedMps = avgSpeed,
                        durationMillis = elapsed,
                        weightLbs = prefs.weightLbs,
                        heightInches = prefs.heightInches,
                        ageYears = prefs.ageYears,
                        isMale = prefs.sex == "male"
                    )
                    val activity = app.repository.getActivity(activityId)
                    activity?.let {
                        app.repository.updateActivity(
                            it.copy(
                                endTime = System.currentTimeMillis(),
                                distanceMeters = distance,
                                durationMillis = elapsed,
                                maxSpeedMps = maxSpeed,
                                avgSpeedMps = avgSpeed,
                                caloriesBurned = calories,
                                status = Activity.STATUS_COMPLETED
                            )
                        )
                    }
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                onFinished()
            }
        }
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
        isActive = false
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
        // Don't re-post the notification after tracking has stopped
        if (!_isTracking.value) return
        val useImperial = (application as VibeRunningApp).preferencesManager.useImperial
        val distance = FormatUtils.formatDistance(totalDistanceMeters, useImperial)
        val duration = FormatUtils.formatDuration(elapsedMillis)
        val pace = FormatUtils.formatPace(
            if (elapsedMillis > 0) totalDistanceMeters / (elapsedMillis / 1000.0) else 0.0,
            useImperial
        )
        val speed = FormatUtils.formatSpeed(lastLocation?.speed?.toDouble() ?: 0.0, useImperial)
        val status = if (isPaused) " (PAUSED)" else ""
        val title = "$duration$status"
        val detail = "$distance  |  Pace: $pace  |  Speed: $speed"

        val notification = buildNotification(title, detail)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        /**
         * True while a service instance in this process is tracking or recovering a run.
         * It resets when the process dies, so an in-progress activity found while this is
         * false was interrupted and nothing is recording it.
         */
        @Volatile
        var isActive = false
            private set

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_DISCARD = "ACTION_DISCARD"
        const val EXTRA_ACTIVITY_ID = "EXTRA_ACTIVITY_ID"
        const val NOTIFICATION_ID = 1
        private const val TAG = "LocationTracking"

        private const val GPS_INTERVAL_MS = 1000L
        private const val GPS_FASTEST_INTERVAL_MS = 500L
        private const val MIN_DISTANCE_METERS = 0f
        private const val MAX_ACCURACY_METERS = 50f
        private const val MAX_SINGLE_DISTANCE_METERS = 100.0
        private const val MAX_REASONABLE_SPEED_MPS = 50.0
        private const val SAVE_INTERVAL_MS = 30_000L
        private const val MOVEMENT_THRESHOLD_METERS = 10.0
        private const val MANUAL_PAUSE_LIMIT_MS = 60 * 60_000L
    }
}
