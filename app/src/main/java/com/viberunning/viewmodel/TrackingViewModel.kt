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

    private val _trackingState = MutableStateFlow(LocationTrackingService.TrackingState())
    val trackingState: StateFlow<LocationTrackingService.TrackingState> = _trackingState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentActivityId = MutableStateFlow(-1L)
    val currentActivityId: StateFlow<Long> = _currentActivityId.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as LocationTrackingService.TrackingBinder).getService()
            trackingService = service
            isBound = true

            viewModelScope.launch {
                service.trackingState.collect { state ->
                    _trackingState.value = state
                }
            }
            viewModelScope.launch {
                service.isTracking.collect { tracking ->
                    _isTracking.value = tracking
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isBound = false
        }
    }

    fun startGpsMonitoring() {
        gpsMonitor.startMonitoring()
    }

    fun stopGpsMonitoring() {
        gpsMonitor.stopMonitoring()
    }

    fun startActivity() {
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
            context.bindService(
                Intent(context, LocationTrackingService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
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
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        _isTracking.value = false
    }

    fun discardActivity() {
        val id = _currentActivityId.value
        val context = getApplication<VibeRunningApp>()
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        _isTracking.value = false
        if (id != -1L) {
            viewModelScope.launch {
                repository.deleteActivity(id)
            }
        }
    }

    fun bindToService() {
        val context = getApplication<VibeRunningApp>()
        context.bindService(
            Intent(context, LocationTrackingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        super.onCleared()
        gpsMonitor.stopMonitoring()
        if (isBound) {
            getApplication<VibeRunningApp>().unbindService(serviceConnection)
            isBound = false
        }
    }
}
