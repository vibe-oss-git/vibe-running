package com.viberunning

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.viberunning.ui.navigation.Screen
import com.viberunning.ui.navigation.bottomNavItems
import com.viberunning.ui.screens.ActivityDetailScreen
import com.viberunning.ui.screens.HistoryScreen
import com.viberunning.ui.screens.HomeScreen
import com.viberunning.ui.screens.StatsScreen
import com.viberunning.ui.theme.VibeRunningTheme
import com.viberunning.viewmodel.HistoryViewModel
import com.viberunning.viewmodel.StatsViewModel
import com.viberunning.viewmodel.TrackingViewModel

class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled in UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            VibeRunningTheme {
                VibeRunningNavHost()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            locationPermissionRequest.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun VibeRunningNavHost() {
    val navController = rememberNavController()
    val trackingViewModel: TrackingViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val statsViewModel: StatsViewModel = viewModel()

    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as VibeRunningApp
    var useImperial by rememberSaveable { mutableStateOf(app.preferencesManager.useImperial) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = trackingViewModel,
                    useImperial = useImperial,
                    onActivityCompleted = { activityId ->
                        navController.navigate(Screen.ActivityDetail.createRoute(activityId))
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    useImperial = useImperial,
                    onActivityClick = { activityId ->
                        navController.navigate(Screen.ActivityDetail.createRoute(activityId))
                    }
                )
            }

            composable(Screen.Stats.route) {
                StatsScreen(
                    viewModel = statsViewModel,
                    useImperial = useImperial
                )
            }

            composable(
                route = Screen.ActivityDetail.route,
                arguments = listOf(navArgument("activityId") { type = NavType.LongType })
            ) { backStackEntry ->
                val activityId = backStackEntry.arguments?.getLong("activityId") ?: return@composable
                ActivityDetailScreen(
                    activityId = activityId,
                    viewModel = historyViewModel,
                    useImperial = useImperial,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
