package com.viberunning.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viberunning.VibeRunningApp
import com.viberunning.data.model.Activity
import com.viberunning.service.LocationTrackingService
import com.viberunning.util.CalorieEstimator
import com.viberunning.util.GpsStatusMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VibeRunningApp
    private val repository = app.repository
    private val gpsMonitor = app.gpsStatusMonitor

    val gpsSignal: StateFlow<GpsStatusMonitor.GpsSignal> = gpsMonitor.signal

    private var isBound = false
    private var serviceCollectors: List<Job> = emptyList()
    // Set between tapping Start and the service reporting it is tracking,
    // so a double tap can't create two activities.
    private var isStarting = false

    private val _trackingState = MutableStateFlow(LocationTrackingService.TrackingState())
    val trackingState: StateFlow<LocationTrackingService.TrackingState> = _trackingState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentActivityId = MutableStateFlow(-1L)
    val currentActivityId: StateFlow<Long> = _currentActivityId.asStateFlow()

    // A run left in progress with nothing tracking it (Android killed the app and
    // didn't restart the service). The UI asks whether to save or discard it.
    private val _interruptedActivity = MutableStateFlow<Activity?>(null)
    val interruptedActivity: StateFlow<Activity?> = _interruptedActivity.asStateFlow()

    init {
        checkForInProgressActivity()
    }

    private fun checkForInProgressActivity() {
        viewModelScope.launch {
            val inProgress = repository.getInProgressActivity() ?: return@launch
            if (LocationTrackingService.isActive) {
                // Still being tracked — reconnect to the running service
                _currentActivityId.value = inProgress.id
                bind()
            } else {
                _interruptedActivity.value = inProgress
                // Without auto-create: connects only if Android restarts the service
                // and it resumes this run, in which case the prompt is withdrawn.
                bind(autoCreate = false)
            }
        }
    }

    /** Saves an interrupted run with the values recorded up to its last periodic save. */
    fun saveInterruptedActivity() {
        val activity = _interruptedActivity.value ?: return
        _interruptedActivity.value = null
        // The service may have resumed this run since the prompt appeared
        if (LocationTrackingService.isActive) return
        viewModelScope.launch {
            val prefs = app.preferencesManager
            val calories = CalorieEstimator.estimate(
                avgSpeedMps = activity.avgSpeedMps,
                durationMillis = activity.durationMillis,
                weightLbs = prefs.weightLbs,
                heightInches = prefs.heightInches,
                ageYears = prefs.ageYears,
                isMale = prefs.sex == "male"
            )
            val endTime = repository.getLastLocationTime(activity.id)
                ?: (activity.startTime + activity.durationMillis)
            repository.updateActivity(
                activity.copy(
                    endTime = endTime,
                    caloriesBurned = calories,
                    status = Activity.STATUS_COMPLETED
                )
            )
            // There may be more than one
            checkForInProgressActivity()
        }
    }

    fun discardInterruptedActivity() {
        val activity = _interruptedActivity.value ?: return
        _interruptedActivity.value = null
        // The service may have resumed this run since the prompt appeared
        if (LocationTrackingService.isActive) return
        viewModelScope.launch {
            repository.deleteActivity(activity.id)
            checkForInProgressActivity()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as LocationTrackingService.TrackingBinder).getService()
            serviceCollectors.forEach { it.cancel() }
            serviceCollectors = listOf(
                viewModelScope.launch {
                    service.trackingState.collect { state ->
                        _trackingState.value = state
                        if (state.activityId != -1L) {
                            _currentActivityId.value = state.activityId
                        }
                    }
                },
                viewModelScope.launch {
                    service.isTracking.collect { tracking ->
                        _isTracking.value = tracking
                        if (tracking) {
                            isStarting = false
                            // The service resumed the run, so it's no longer interrupted
                            _interruptedActivity.value = null
                        }
                    }
                }
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stopCollecting()
        }
    }

    fun startGpsMonitoring() {
        gpsMonitor.startMonitoring()
    }

    fun stopGpsMonitoring() {
        gpsMonitor.stopMonitoring()
    }

    fun startActivity() {
        if (isStarting || _isTracking.value) return
        isStarting = true
        gpsMonitor.stopMonitoring()
        viewModelScope.launch {
            val activityId = repository.createActivity()
            _currentActivityId.value = activityId

            val context = getApplication<VibeRunningApp>()
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_START
                putExtra(LocationTrackingService.EXTRA_ACTIVITY_ID, activityId)
            }
            context.startForegroundService(intent)
            bind()
        }
    }

    fun pauseActivity() {
        val context = getApplication<VibeRunningApp>()
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeActivity() {
        val context = getApplication<VibeRunningApp>()
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopActivity() {
        val context = getApplication<VibeRunningApp>()
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)
        unbind()
        _isTracking.value = false
        isStarting = false
    }

    fun discardActivity() {
        // The service deletes the activity after it stops recording, so no
        // location point can be written for an activity that no longer exists.
        val context = getApplication<VibeRunningApp>()
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_DISCARD
            putExtra(LocationTrackingService.EXTRA_ACTIVITY_ID, _currentActivityId.value)
        }
        context.startService(intent)
        unbind()
        _isTracking.value = false
        isStarting = false
    }

    private fun unbind() {
        if (isBound) {
            getApplication<VibeRunningApp>().unbindService(serviceConnection)
            isBound = false
        }
        stopCollecting()
    }

    private fun stopCollecting() {
        serviceCollectors.forEach { it.cancel() }
        serviceCollectors = emptyList()
    }

    private fun bind(autoCreate: Boolean = true) {
        if (isBound) return
        val context = getApplication<VibeRunningApp>()
        // Track the binding from here, not onServiceConnected(), so an unbind
        // before the connection completes still releases it.
        isBound = context.bindService(
            Intent(context, LocationTrackingService::class.java),
            serviceConnection,
            if (autoCreate) Context.BIND_AUTO_CREATE else 0
        )
    }

    override fun onCleared() {
        super.onCleared()
        gpsMonitor.stopMonitoring()
        unbind()
    }
}
