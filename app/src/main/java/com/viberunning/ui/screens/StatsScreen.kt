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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.viberunning.ui.components.StatsCard
import com.viberunning.util.FormatUtils
import com.viberunning.viewmodel.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    useImperial: Boolean
) {
    val topSpeed by viewModel.topSpeed.collectAsState()
    val longestDistance by viewModel.longestDistance.collectAsState()
    val fastestPace by viewModel.fastestPace.collectAsState()
    val longestDuration by viewModel.longestDuration.collectAsState()
    val totalActivities by viewModel.totalActivities.collectAsState()
    val totalDistance by viewModel.totalDistance.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()

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
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    label = "Top Speed",
                    value = topSpeed?.let { FormatUtils.formatSpeed(it, useImperial) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    label = "Longest Distance",
                    value = longestDistance?.let { FormatUtils.formatDistance(it, useImperial) } ?: "--",
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    label = "Longest Run",
                    value = longestDuration?.let {
                        FormatUtils.formatDuration(it.durationMillis)
                    } ?: "--",
                    modifier = Modifier.weight(1f)
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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
