package com.viberunning.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viberunning.VibeRunningApp
import com.viberunning.data.model.Activity
import com.viberunning.data.model.LocationPoint
import com.viberunning.util.KmlExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as VibeRunningApp).repository

    val activities: StateFlow<List<Activity>> = repository.observeCompletedActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedActivity = MutableStateFlow<Activity?>(null)
    val selectedActivity: StateFlow<Activity?> = _selectedActivity.asStateFlow()

    private val _selectedActivityPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val selectedActivityPoints: StateFlow<List<LocationPoint>> = _selectedActivityPoints.asStateFlow()

    private var loadJobs: List<Job> = emptyList()

    // Observes rather than reads once: after Stop & Save the detail screen opens
    // before the service has written the final values, and updates when it does.
    fun loadActivity(id: Long) {
        loadJobs.forEach { it.cancel() }
        _selectedActivity.value = null
        _selectedActivityPoints.value = emptyList()
        loadJobs = listOf(
            viewModelScope.launch {
                repository.observeActivity(id).collect { _selectedActivity.value = it }
            },
            viewModelScope.launch {
                repository.observeLocationPoints(id).collect { _selectedActivityPoints.value = it }
            }
        )
    }

    fun deleteActivity(id: Long) {
        loadJobs.forEach { it.cancel() }
        loadJobs = emptyList()
        viewModelScope.launch {
            repository.deleteActivity(id)
            _selectedActivity.value = null
            _selectedActivityPoints.value = emptyList()
        }
    }

    fun exportActivityToKml(activityId: Long): Uri? {
        val context: Context = getApplication()
        val activity = _selectedActivity.value ?: return null
        val points = _selectedActivityPoints.value
        if (points.isEmpty()) return null
        val useImperial = (context as VibeRunningApp).preferencesManager.useImperial
        return KmlExporter.exportToKml(context, activity, points, useImperial)
    }
}
