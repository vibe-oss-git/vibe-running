package com.viberunning.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.viberunning.ui.components.DataBackupSection
import com.viberunning.util.PreferencesManager
import com.viberunning.viewmodel.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    useImperial: Boolean,
    onToggleUnits: (Boolean) -> Unit,
    backupViewModel: BackupViewModel,
    onSettingsImported: () -> Unit,
    onBack: () -> Unit
) {
    var pauseMinutes by remember { mutableIntStateOf(preferencesManager.inactivityPauseMinutes) }
    var exitMinutes by remember { mutableIntStateOf(preferencesManager.inactivityExitMinutes) }

    // Show imported settings
    val settingsImported by backupViewModel.settingsImported.collectAsState()
    LaunchedEffect(settingsImported) {
        if (settingsImported > 0) {
            pauseMinutes = preferencesManager.inactivityPauseMinutes
            exitMinutes = preferencesManager.inactivityExitMinutes
            onSettingsImported()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Imperial Units",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (useImperial) "Miles, mph" else "Kilometers, km/h",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useImperial,
                    onCheckedChange = onToggleUnits
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            MinutesOption(
                title = "Auto-pause after",
                description = "Pause the run when you haven't moved for this long",
                options = PreferencesManager.INACTIVITY_PAUSE_OPTIONS,
                selected = pauseMinutes,
                isEnabled = { true },
                onSelect = { minutes ->
                    pauseMinutes = minutes
                    preferencesManager.inactivityPauseMinutes = minutes
                    // Ending the run can't come before the pause
                    if (exitMinutes < minutes) {
                        exitMinutes = minutes
                        preferencesManager.inactivityExitMinutes = minutes
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            MinutesOption(
                title = "End run after",
                description = "Save and end the run when you haven't moved for this long. " +
                    "Must be at least the auto-pause time.",
                options = PreferencesManager.INACTIVITY_EXIT_OPTIONS,
                selected = exitMinutes,
                isEnabled = { it >= pauseMinutes },
                onSelect = { minutes ->
                    exitMinutes = minutes
                    preferencesManager.inactivityExitMinutes = minutes
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Changes apply from your next run.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            DataBackupSection(viewModel = backupViewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinutesOption(
    title: String,
    description: String,
    options: List<Int>,
    selected: Int,
    isEnabled: (Int) -> Boolean,
    onSelect: (Int) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge
    )
    Text(
        text = description,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                enabled = isEnabled(minutes),
                label = { Text("$minutes min") }
            )
        }
    }
}
