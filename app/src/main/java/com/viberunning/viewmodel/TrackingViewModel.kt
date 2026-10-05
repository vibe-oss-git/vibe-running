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
import com.viberunning.service.LocationTrackingService
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

    private var trackingService: LocationTrackingService? = null
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

    init {
        // Check for an in-progress activity and reconnect to the running service
        viewModelScope.launch {
            val inProgress = repository.getInProgressActivity()
            if (inProgress != null) {
                _currentActivityId.value = inProgress.id
                bind()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as LocationTrackingService.TrackingBinder).getService()
            trackingService = service

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
                        if (tracking) isStarting = false
                    }
                }
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
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
        trackingService = null
        stopCollecting()
    }

    private fun stopCollecting() {
        serviceCollectors.forEach { it.cancel() }
        serviceCollectors = emptyList()
    }

    fun bindToService() {
        bind()
    }

    private fun bind() {
        if (isBound) return
        val context = getApplication<VibeRunningApp>()
        // Track the binding from here, not onServiceConnected(), so an unbind
        // before the connection completes still releases it.
        isBound = context.bindService(
            Intent(context, LocationTrackingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        super.onCleared()
        gpsMonitor.stopMonitoring()
        unbind()
    }
}
