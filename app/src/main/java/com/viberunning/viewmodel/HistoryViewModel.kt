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

    fun loadActivity(id: Long) {
        viewModelScope.launch {
            _selectedActivity.value = repository.getActivity(id)
            _selectedActivityPoints.value = repository.getLocationPoints(id)
        }
    }

    fun deleteActivity(id: Long) {
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
        return KmlExporter.exportToKml(context, activity, points)
    }
}
