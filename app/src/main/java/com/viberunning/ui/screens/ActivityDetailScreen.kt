package com.viberunning.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.viberunning.ui.components.SpeedMapView
import com.viberunning.ui.components.StatsCard
import com.viberunning.util.FormatUtils
import com.viberunning.util.KmlExporter
import com.viberunning.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: Long,
    viewModel: HistoryViewModel,
    useImperial: Boolean,
    onBack: () -> Unit
) {
    val activity by viewModel.selectedActivity.collectAsState()
    val points by viewModel.selectedActivityPoints.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        viewModel.loadActivity(activityId)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Activity") },
            text = { Text("Are you sure you want to delete this activity? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteActivity(activityId)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        activity?.let { act ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Date header with day of week
                Text(
                    text = "${FormatUtils.formatDayOfWeek(act.startTime)}, ${FormatUtils.formatDate(act.startTime)}",
                    style = MaterialTheme.typography.titleLarge
                )
                val endTimeText = act.endTime?.let { FormatUtils.formatTime(it) } ?: "—"
                Text(
                    text = "${FormatUtils.formatTime(act.startTime)} – $endTimeText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Speed map — larger by default; tap to enlarge further for pinch-zoom
                SpeedMapView(
                    points = points,
                    useImperial = useImperial,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        label = "Distance",
                        value = FormatUtils.formatDistance(act.distanceMeters, useImperial),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        label = "Duration",
                        value = FormatUtils.formatDuration(act.durationMillis),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        label = "Avg Pace",
                        value = FormatUtils.formatPace(act.avgSpeedMps, useImperial),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        label = "Avg Speed",
                        value = FormatUtils.formatSpeed(act.avgSpeedMps, useImperial),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        label = "Max Speed",
                        value = FormatUtils.formatSpeed(act.maxSpeedMps, useImperial),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        label = "Calories",
                        value = if (act.caloriesBurned > 0) "%,d kcal".format(act.caloriesBurned) else "—",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        label = "GPS Points",
                        value = "${points.size}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Elevation gain/loss — only show if valid altitude data exists
                val elevationChange = remember(points) {
                    computeElevationChange(points)
                }
                if (elevationChange != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatsCard(
                            label = "Elev. Gain",
                            value = FormatUtils.formatElevation(elevationChange.first, useImperial),
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            label = "Elev. Loss",
                            value = FormatUtils.formatElevation(elevationChange.second, useImperial),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Export KML button
                OutlinedButton(
                    onClick = {
                        val uri = viewModel.exportActivityToKml(activityId)
                        uri?.let {
                            val shareIntent = KmlExporter.createShareIntent(it)
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Export activity as KML")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = points.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Export to KML (Google Earth)")
                }

                if (points.isEmpty()) {
                    Text(
                        text = "No GPS data recorded for this activity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

// Computes cumulative elevation gain and loss (in meters) across the activity.
// Uses the same zero-culling logic as the map: 0.0 altitude values are treated
// as missing GPS data and excluded, unless all readings are near sea level.
// Returns (gain, loss) in meters, or null if no valid altitude data exists.
private fun computeElevationChange(points: List<com.viberunning.data.model.LocationPoint>): Pair<Double, Double>? {
    if (points.size < 2) return null

    val nonZeroAltitudes = points.filter { it.altitude != 0.0 }
    val validPoints = if (nonZeroAltitudes.isEmpty()) {
        return null
    } else if (nonZeroAltitudes.all { abs(it.altitude) < 30.0 }) {
        // Near sea level — keep all points including 0.0
        points
    } else if (nonZeroAltitudes.size < 2) {
        return null
    } else {
        // Cull 0.0 values as missing data
        nonZeroAltitudes
    }

    // Sum positive deltas as gain, negative deltas as loss
    var totalGain = 0.0
    var totalLoss = 0.0
    for (i in 1 until validPoints.size) {
        val delta = validPoints[i].altitude - validPoints[i - 1].altitude
        if (delta > 0) totalGain += delta else totalLoss += -delta
    }

    if (totalGain == 0.0 && totalLoss == 0.0) return null
    return Pair(totalGain, totalLoss)
}
