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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.viberunning.data.model.Activity
import com.viberunning.ui.components.WeightPromptDialog
import com.viberunning.ui.navigation.Screen
import com.viberunning.ui.navigation.bottomNavItems
import com.viberunning.ui.screens.ActivityDetailScreen
import com.viberunning.ui.screens.HistoryScreen
import com.viberunning.ui.screens.HomeScreen
import com.viberunning.ui.screens.ProfileScreen
import com.viberunning.ui.screens.SettingsScreen
import com.viberunning.ui.screens.StatsScreen
import com.viberunning.ui.theme.VibeRunningTheme
import com.viberunning.util.FormatUtils
import com.viberunning.viewmodel.HistoryViewModel
import com.viberunning.viewmodel.StatsViewModel
import com.viberunning.viewmodel.TrackingViewModel

class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // The Run tab may have asked for GPS status before permission was granted
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            (application as VibeRunningApp).gpsStatusMonitor.onPermissionGranted()
        }
    }

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
    val prefs = app.preferencesManager
    var useImperial by rememberSaveable { mutableStateOf(prefs.useImperial) }

    // Weight prompt state
    var showWeightPrompt by remember { mutableStateOf(false) }
    var weightPromptHandled by remember { mutableStateOf(false) }

    // On first composition, check if we need profile setup or weight prompt
    LaunchedEffect(Unit) {
        if (!prefs.hasProfile) {
            navController.navigate(Screen.Profile.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        } else if (prefs.isWeightStale && !weightPromptHandled) {
            showWeightPrompt = true
        }
    }

    // Run left in progress when Android killed the app
    val interruptedActivity by trackingViewModel.interruptedActivity.collectAsState()
    interruptedActivity?.let { activity ->
        InterruptedRunDialog(
            activity = activity,
            useImperial = useImperial,
            onSave = { trackingViewModel.saveInterruptedActivity() },
            onDiscard = { trackingViewModel.discardInterruptedActivity() }
        )
    }

    // Weight prompt dialog (after any interrupted run is dealt with)
    if (showWeightPrompt && interruptedActivity == null) {
        WeightPromptDialog(
            preferencesManager = prefs,
            useImperial = useImperial,
            onDismiss = {
                showWeightPrompt = false
                weightPromptHandled = true
            },
            onSave = {
                showWeightPrompt = false
                weightPromptHandled = true
            }
        )
    }

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
                    useImperial = useImperial,
                    onActivityClick = { activityId ->
                        navController.navigate(Screen.ActivityDetail.createRoute(activityId))
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    preferencesManager = prefs,
                    useImperial = useImperial,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onProfileSaved = {
                        if (navController.previousBackStackEntry == null) {
                            // First-launch: navigate to Home
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Profile.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    preferencesManager = prefs,
                    useImperial = useImperial,
                    onToggleUnits = { imperial ->
                        useImperial = imperial
                        prefs.useImperial = imperial
                    },
                    backupViewModel = viewModel(),
                    onSettingsImported = { useImperial = prefs.useImperial },
                    onBack = { navController.popBackStack() }
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

@Composable
private fun InterruptedRunDialog(
    activity: Activity,
    useImperial: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        // Require a choice; tapping outside does nothing
        onDismissRequest = {},
        title = { Text("Unfinished Run") },
        text = {
            Text(
                "The run you started ${FormatUtils.formatDateTime(activity.startTime)} " +
                    "was interrupted when the app was closed by Android.\n\n" +
                    "Recorded: ${FormatUtils.formatDistance(activity.distanceMeters, useImperial)} " +
                    "in ${FormatUtils.formatDuration(activity.durationMillis)}. " +
                    "Up to the last 30 seconds before it stopped may be missing."
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
