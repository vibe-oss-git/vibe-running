package com.viberunning.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Run", Icons.Default.DirectionsRun)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Stats : Screen("stats", "Stats", Icons.Default.Leaderboard)
    data object Profile : Screen("profile", "Profile", Icons.Default.Person)
    data object ActivityDetail : Screen("activity/{activityId}", "Detail", Icons.Default.History) {
        fun createRoute(activityId: Long) = "activity/$activityId"
    }
}

val bottomNavItems = listOf(Screen.Home, Screen.History, Screen.Stats, Screen.Profile)
