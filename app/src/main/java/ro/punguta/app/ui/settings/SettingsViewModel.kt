package ro.punguta.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.punguta.app.AppContainer
import ro.punguta.app.R
import ro.punguta.app.data.repo.BackupSummary
import ro.punguta.app.domain.backup.BackupFile
import ro.punguta.app.domain.backup.BackupProblem
import ro.punguta.app.domain.backup.InvalidBackupException
import ro.punguta.app.ui.appViewModelFactory
import java.io.IOException
import java.time.LocalDate

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    /** Valid backup read; waiting for the user to confirm replacing all data. */
    data class ConfirmRestore(val file: BackupFile, val summary: BackupSummary) : BackupUi
    data class Problem(val message: Int) : BackupUi
}

class SettingsViewModel(private val c: AppContainer, private val resolver: ContentResolver) : ViewModel() {
    val lockEnabled: StateFlow<Boolean> = c.settings.lockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val lastExport: StateFlow<Long?> = c.settings.lastExportAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _backup = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val backup = _backup.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()

    fun setLock(enabled: Boolean) = viewModelScope.launch { c.settings.setLockEnabled(enabled) }

    fun suggestedFileName(): String = "punguta-backup-${LocalDate.now()}.json"

    fun export(uri: Uri) = viewModelScope.launch {
        _backup.value = BackupUi.Working
        try {
            val json = c.backup.exportJson()
            withContext(Dispatchers.IO) {
                val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            c.settings.setLastExportAt(System.currentTimeMillis())
            _backup.value = BackupUi.Idle
            _message.value = R.string.backup_export_done
        } catch (e: IOException) {
            _backup.value = BackupUi.Problem(R.string.backup_export_failed)
        } catch (e: SecurityException) {
            _backup.value = BackupUi.Problem(R.string.backup_export_failed)
        }
    }

    /** Reads and validates the file; nothing is changed until [confirmRestore]. */
    fun inspect(uri: Uri) = viewModelScope.launch {
        _backup.value = BackupUi.Working
        _backup.value = try {
            val text = withContext(Dispatchers.IO) { readLimited(uri) }
            val (file, summary) = withContext(Dispatchers.Default) { c.backup.inspect(text) }
            BackupUi.ConfirmRestore(file, summary)
        } catch (e: InvalidBackupException) {
            BackupUi.Problem(
                when (e.problem) {
                    BackupProblem.NOT_JSON, BackupProblem.NOT_A_BACKUP -> R.string.backup_not_a_backup
                    BackupProblem.NEWER_VERSION -> R.string.backup_newer_version
                    BackupProblem.INVALID_DATA -> R.string.backup_invalid_data
                },
            )
        } catch (e: IOException) {
            BackupUi.Problem(R.string.backup_read_failed)
        } catch (e: SecurityException) {
            BackupUi.Problem(R.string.backup_read_failed)
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        val pending = _backup.value as? BackupUi.ConfirmRestore ?: return@launch
        _backup.value = BackupUi.Working
        try {
            c.backup.restore(pending.file)
            c.recurring.generateDue(LocalDate.now())
            _backup.value = BackupUi.Idle
            _message.value = R.string.backup_restore_done
        } catch (e: Exception) {
            // The restore transaction rolled back; existing data is unchanged.
            _backup.value = BackupUi.Problem(R.string.backup_restore_failed)
        }
    }

    fun dismiss() {
        _backup.value = BackupUi.Idle
    }

    fun messageShown() {
        _message.value = null
    }

    private fun readLimited(uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: throw IOException("No input stream")
        return input.use { stream ->
            val bytes = stream.readNBytesCompat(MAX_BACKUP_BYTES + 1)
            if (bytes.size > MAX_BACKUP_BYTES) throw InvalidBackupException(BackupProblem.INVALID_DATA, "too large")
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        val Factory = appViewModelFactory { c, _ -> SettingsViewModel(c, c.appContext.contentResolver) }
    }
}
