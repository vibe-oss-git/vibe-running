package com.viberunning.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viberunning.VibeRunningApp
import com.viberunning.data.model.Activity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as VibeRunningApp).repository

    val topSpeed: StateFlow<Activity?> = repository.observeTopSpeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val longestDistance: StateFlow<Activity?> = repository.observeLongestDistance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fastestPace: StateFlow<Activity?> = repository.observeFastestPace()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val longestDuration: StateFlow<Activity?> = repository.observeLongestDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalActivities: StateFlow<Int> = repository.observeTotalActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDistance: StateFlow<Double?> = repository.observeTotalDistance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalDuration: StateFlow<Long?> = repository.observeTotalDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalCalories: StateFlow<Int?> = repository.observeTotalCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val completedActivities: StateFlow<List<Activity>> = repository.observeCompletedActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
