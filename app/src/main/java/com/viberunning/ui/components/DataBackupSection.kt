package com.viberunning.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.viberunning.data.backup.BackupFile
import com.viberunning.data.backup.BackupSection
import com.viberunning.util.FormatUtils
import com.viberunning.viewmodel.BackupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export and import of profile data, settings, and activity history. The user picks
 * which sections to include, then where the file goes (or which file to read) through
 * the system file picker. Nothing leaves the phone unless the user moves the file.
 */
@Composable
fun DataBackupSection(viewModel: BackupViewModel) {
    val busyMessage by viewModel.busyMessage.collectAsState()
    val resultMessage by viewModel.resultMessage.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    // Kept across the trip to the file picker
    var exportSections by rememberSaveable { mutableStateOf(BackupSection.entries.toSet()) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFile.MIME_TYPE)
    ) { uri -> uri?.let { viewModel.export(it, exportSections) } }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.inspectImport(it) } }

    Text(text = "Backup", style = MaterialTheme.typography.bodyLarge)
    Text(
        text = "Save your data to a file, or add data from one. Use this to move to a new " +
            "phone or keep a copy. The file is saved where you choose; nothing is uploaded.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            exportSections = BackupSection.entries.toSet()
            showExportDialog = true
        }) {
            Text("Export data")
        }
        OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }) {
            Text("Import data")
        }
    }

    if (showExportDialog) {
        SectionPickerDialog(
            title = "Export Data",
            intro = "Choose what to include. You'll then pick where to save the file.",
            options = BackupSection.entries.map { it to it.label },
            selected = exportSections,
            onSelectedChange = { exportSections = it },
            confirmLabel = "Export",
            onConfirm = {
                showExportDialog = false
                val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                createFile.launch("vibe-running-backup-$date.json")
            },
            onDismiss = { showExportDialog = false }
        )
    }

    pendingImport?.let { (_, info) ->
        var importSections by remember(info) { mutableStateOf(info.sections) }
        val exportedOn = if (info.exportedAt > 0) {
            " from ${FormatUtils.formatDateTime(info.exportedAt)}"
        } else ""
        SectionPickerDialog(
            title = "Import Data",
            intro = "This backup$exportedOn contains the items below. Runs already on this " +
                "phone are skipped. Imported profile data and settings replace the current ones.",
            // Only what the file contains is offered
            options = BackupSection.entries.filter { it in info.sections }.map { section ->
                section to if (section == BackupSection.ACTIVITY) {
                    "${section.label} (${info.activityCount} ${if (info.activityCount == 1) "run" else "runs"})"
                } else {
                    section.label
                }
            },
            selected = importSections,
            onSelectedChange = { importSections = it },
            confirmLabel = "Import",
            onConfirm = { viewModel.import(importSections) },
            onDismiss = { viewModel.cancelImport() }
        )
    }

    busyMessage?.let { message ->
        AlertDialog(
            // Can't be dismissed while the file is being written or read
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(message)
                }
            }
        )
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResult() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SectionPickerDialog(
    title: String,
    intro: String,
    options: List<Pair<BackupSection, String>>,
    selected: Set<BackupSection>,
    onSelectedChange: (Set<BackupSection>) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                options.forEach { (section, label) ->
                    val checked = section in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on ->
                                    onSelectedChange(if (on) selected + section else selected - section)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selected.isNotEmpty()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
