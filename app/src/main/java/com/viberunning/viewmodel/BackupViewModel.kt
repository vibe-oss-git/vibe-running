package com.viberunning.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viberunning.VibeRunningApp
import com.viberunning.data.backup.BackupFile
import com.viberunning.data.backup.BackupFormatException
import com.viberunning.data.backup.BackupInfo
import com.viberunning.data.backup.BackupSection
import com.viberunning.service.LocationTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VibeRunningApp
    private val backupFile = BackupFile(app.repository, app.preferencesManager)

    /** Shown while a file is being written or read. */
    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    /** Outcome of the last export or import, shown until dismissed. */
    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    /** A file chosen for import and what it contains, waiting for the user to pick sections. */
    private val _pendingImport = MutableStateFlow<Pair<Uri, BackupInfo>?>(null)
    val pendingImport: StateFlow<Pair<Uri, BackupInfo>?> = _pendingImport.asStateFlow()

    /** Incremented when settings were imported, so screens re-read them. */
    private val _settingsImported = MutableStateFlow(0)
    val settingsImported: StateFlow<Int> = _settingsImported.asStateFlow()

    fun export(uri: Uri, sections: Set<BackupSection>) {
        viewModelScope.launch {
            _busyMessage.value = "Exporting…"
            _resultMessage.value = try {
                val count = withContext(Dispatchers.IO) {
                    // "wt" truncates; some storage providers only support "w"
                    val output = runCatching { app.contentResolver.openOutputStream(uri, "wt") }
                        .getOrNull()
                        ?: app.contentResolver.openOutputStream(uri, "w")
                        ?: error("Couldn't open the file")
                    output.use { backupFile.export(it, sections) }
                }
                buildString {
                    append("Export complete.")
                    if (BackupSection.ACTIVITY in sections) {
                        append(" ${runs(count)} saved.")
                    }
                }
            } catch (e: Exception) {
                "Export failed: ${e.message ?: "unknown error"}"
            } finally {
                _busyMessage.value = null
            }
        }
    }

    /** Reads what a chosen file contains so the user can pick what to import. */
    fun inspectImport(uri: Uri) {
        if (LocationTrackingService.isActive) {
            _resultMessage.value = RUN_IN_PROGRESS
            return
        }
        viewModelScope.launch {
            _busyMessage.value = "Reading file…"
            try {
                val info = withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: error("Couldn't open the file")
                    input.use { backupFile.readInfo(it) }
                }
                if (info.sections.isEmpty()) {
                    _resultMessage.value = "This backup doesn't contain anything to import."
                } else {
                    _pendingImport.value = uri to info
                }
            } catch (e: BackupFormatException) {
                _resultMessage.value = e.message
            } catch (e: Exception) {
                _resultMessage.value = "Couldn't read the file: ${e.message ?: "unknown error"}"
            } finally {
                _busyMessage.value = null
            }
        }
    }

    fun import(sections: Set<BackupSection>) {
        val (uri, _) = _pendingImport.value ?: return
        _pendingImport.value = null
        if (LocationTrackingService.isActive) {
            _resultMessage.value = RUN_IN_PROGRESS
            return
        }
        viewModelScope.launch {
            _busyMessage.value = "Importing…"
            try {
                val result = withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: error("Couldn't open the file")
                    input.use { backupFile.import(it, sections) }
                }
                _resultMessage.value = buildString {
                    append("Import complete.")
                    if (BackupSection.ACTIVITY in sections) {
                        append(" ${runs(result.importedActivities)} added.")
                        if (result.skippedDuplicates > 0) {
                            append(" ${runs(result.skippedDuplicates)} already on this phone ${if (result.skippedDuplicates == 1) "was" else "were"} skipped.")
                        }
                        if (result.skippedInvalid > 0) {
                            append(" ${runs(result.skippedInvalid)} with invalid data ${if (result.skippedInvalid == 1) "was" else "were"} skipped.")
                        }
                    }
                }
            } catch (e: BackupFormatException) {
                _resultMessage.value = e.message
            } catch (e: Exception) {
                _resultMessage.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                // Settings may have been applied even if activities later failed
                if (BackupSection.SETTINGS in sections) _settingsImported.value++
                _busyMessage.value = null
            }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun dismissResult() {
        _resultMessage.value = null
    }

    private fun runs(count: Int) = if (count == 1) "1 run" else "$count runs"

    private companion object {
        const val RUN_IN_PROGRESS = "Finish or discard the current run before importing."
    }
}
