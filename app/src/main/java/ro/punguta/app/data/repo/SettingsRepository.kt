package ro.punguta.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.SettingEntity

/** App settings, stored in Room so they are included in backups. */
class SettingsRepository(db: AppDatabase) {
    private val dao = db.settingDao()

    val lockEnabled: Flow<Boolean> = dao.observe(KEY_LOCK).map { it == "true" }

    suspend fun isLockEnabled(): Boolean = dao.get(KEY_LOCK) == "true"

    suspend fun setLockEnabled(enabled: Boolean) = dao.put(SettingEntity(KEY_LOCK, enabled.toString()))

    /** Epoch millis of the last successful export, or null if never exported. */
    val lastExportAt: Flow<Long?> = dao.observe(KEY_LAST_EXPORT).map { it?.toLongOrNull() }

    suspend fun setLastExportAt(millis: Long) = dao.put(SettingEntity(KEY_LAST_EXPORT, millis.toString()))

    companion object {
        const val KEY_LOCK = "lock_enabled"
        const val KEY_LAST_EXPORT = "last_export_at"
    }
}
