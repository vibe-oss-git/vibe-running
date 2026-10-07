package com.viberunning.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.viberunning.data.model.Activity
import com.viberunning.data.model.PersonalRecord
import com.viberunning.ui.components.MetricChart
import com.viberunning.ui.components.StatsCard
import com.viberunning.util.FormatUtils
import com.viberunning.viewmodel.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    useImperial: Boolean,
    onActivityClick: (Long) -> Unit = {}
) {
    val topSpeed by viewModel.topSpeed.collectAsState()
    val longestDistance by viewModel.longestDistance.collectAsState()
    val fastestPace by viewModel.fastestPace.collectAsState()
    val longestDuration by viewModel.longestDuration.collectAsState()
    val totalActivities by viewModel.totalActivities.collectAsState()
    val totalDistance by viewModel.totalDistance.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val totalCalories by viewModel.totalCalories.collectAsState()
    val completedActivities by viewModel.completedActivities.collectAsState()

    // Record the user long-pressed, waiting for confirmation
    var pendingDisregard by remember { mutableStateOf<Pair<PersonalRecord, Activity>?>(null) }
    pendingDisregard?.let { (record, activity) ->
        AlertDialog(
            onDismissRequest = { pendingDisregard = null },
            title = { Text("Disregard ${record.label}?") },
            text = {
                Text(
                    "${recordValue(record, activity, useImperial)} from your run on " +
                        "${FormatUtils.formatDayOfWeek(activity.startTime)}, " +
                        "${FormatUtils.formatDate(activity.startTime)} will no longer count as your " +
                        "${record.label}. The next best run will be shown instead.\n\n" +
                        "The run stays in your history and still counts toward your other records " +
                        "and lifetime totals. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disregardRecord(activity.id, record)
                    pendingDisregard = null
                }) {
                    Text("Disregard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisregard = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Your Records",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (totalActivities == 0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Complete your first activity\nto see your stats here!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Personal records
            Text(
                text = "Personal Records",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tap a record to see the run. Long-press to disregard it, e.g. after a GPS glitch.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    label = "Top Speed",
                    value = topSpeed?.let { FormatUtils.formatSpeed(it.maxSpeedMps, useImperial) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = topSpeed?.let { { onActivityClick(it.id) } },
                    onLongClick = topSpeed?.let { { pendingDisregard = PersonalRecord.TOP_SPEED to it } }
                )
                StatsCard(
                    label = "Longest Distance",
                    value = longestDistance?.let { FormatUtils.formatDistance(it.distanceMeters, useImperial) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = longestDistance?.let { { onActivityClick(it.id) } },
                    onLongClick = longestDistance?.let { { pendingDisregard = PersonalRecord.LONGEST_DISTANCE to it } }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    label = "Best Pace",
                    value = fastestPace?.let {
                        FormatUtils.formatPace(it.avgSpeedMps, useImperial)
                    } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = fastestPace?.let { { onActivityClick(it.id) } },
                    onLongClick = fastestPace?.let { { pendingDisregard = PersonalRecord.BEST_PACE to it } }
                )
                StatsCard(
                    label = "Longest Run",
                    value = longestDuration?.let {
                        FormatUtils.formatDuration(it.durationMillis)
                    } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = longestDuration?.let { { onActivityClick(it.id) } },
                    onLongClick = longestDuration?.let { { pendingDisregard = PersonalRecord.LONGEST_RUN to it } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Lifetime totals
            Text(
                text = "Lifetime Totals",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    label = "Activities",
                    value = "$totalActivities",
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    label = "Total Distance",
                    value = totalDistance?.let { FormatUtils.formatDistance(it, useImperial) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    label = "Total Time",
                    value = totalDuration?.let { FormatUtils.formatDuration(it) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    label = "Avg Distance",
                    value = if (totalActivities > 0 && totalDistance != null) {
                        FormatUtils.formatDistance(totalDistance!! / totalActivities, useImperial)
                    } else "--",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    label = "Total Calories",
                    value = totalCalories?.let { "%,d kcal".format(it) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    label = "Avg Calories",
                    value = if (totalActivities > 0 && totalCalories != null) {
                        "%,d kcal".format(totalCalories!! / totalActivities)
                    } else "--",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Trends",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            MetricChart(
                activities = completedActivities,
                useImperial = useImperial
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun recordValue(record: PersonalRecord, activity: Activity, useImperial: Boolean): String =
    when (record) {
        PersonalRecord.TOP_SPEED -> FormatUtils.formatSpeed(activity.maxSpeedMps, useImperial)
        PersonalRecord.LONGEST_DISTANCE -> FormatUtils.formatDistance(activity.distanceMeters, useImperial)
        PersonalRecord.BEST_PACE -> FormatUtils.formatPace(activity.avgSpeedMps, useImperial)
        PersonalRecord.LONGEST_RUN -> FormatUtils.formatDuration(activity.durationMillis)
    }
