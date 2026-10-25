package io.github.arpit92k.fortuna.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.core.store.PreparedBackup
import io.github.arpit92k.fortuna.platform.DocumentFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.IOException

/** What S-17 says after saving. */
enum class BackupMessage {
    SAVED,

    /** The file could not be written where the user chose. */
    NOT_SAVED,

    /** Saved, but a change was made while saving, which the file does not hold. */
    CHANGED_WHILE_SAVING,
}

/** S-17 Backup (UF-12). */
data class BackupUi(
    /** When the last backup was made, or null if there has never been one. */
    val lastBackup: LocalDate? = null,
    /** Whether the last backup holds everything there is now. */
    val upToDate: Boolean = false,
    val saving: Boolean = false,
    val message: BackupMessage? = null,
)

/**
 * Makes a backup and saves it where the user picked. The file is made only
 * once the user has chosen where it goes, so it holds everything up to then,
 * and it is recorded as the latest backup only once it has been written.
 *
 * Choosing the place takes the user out of the app, so the app may lock
 * meanwhile. The place is then kept, and the backup is saved on unlocking.
 */
class BackupViewModel(
    private val core: Core,
    private val files: DocumentFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val local = MutableStateFlow(BackupUi())

    val ui: StateFlow<BackupUi> = combine(core.dataset, local) { data, form ->
        val settings = data?.settings
        form.copy(
            lastBackup = settings?.lastBackup,
            upToDate = settings != null && settings.lastBackupRevision == settings.revision,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUi())

    /** A place chosen while the app was locked, saved to on unlocking. */
    private var pending: String? = null

    init {
        viewModelScope.launch {
            core.state.collect { state ->
                val location = pending
                if (state == SessionState.Unlocked && location != null) {
                    pending = null
                    save(location)
                }
            }
        }
    }

    /** The name the picker suggests, with the date so several backups can be told apart. */
    fun suggestedName(today: LocalDate): String = "Fortuna backup $today.fortuna"

    /** The place the picker returned, or null if the user closed it. */
    fun save(location: String?) {
        if (location == null || local.value.saving) return
        local.value = BackupUi(saving = true)
        viewModelScope.launch {
            val backup: PreparedBackup = try {
                core.run { prepareBackup() }
            } catch (e: IllegalStateException) {
                // Locked while the picker was open: saved once unlocked again.
                pending = location
                local.value = BackupUi()
                if (core.state.value == SessionState.Unlocked) save(pending.also { pending = null })
                return@launch
            }
            val written = try {
                withContext(io) { files.write(location, backup.file) }
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
            val message = when {
                !written -> BackupMessage.NOT_SAVED
                // Locked just now: the file is whole, only not yet counted as the latest backup.
                runCatching { core.run { confirmBackupSaved(backup) } }.getOrDefault(true) -> BackupMessage.SAVED
                else -> BackupMessage.CHANGED_WHILE_SAVING
            }
            local.value = BackupUi(message = message)
        }
    }

    /** The message has been read, or the page left. */
    fun clearMessage() {
        local.update { it.copy(message = null) }
    }
}
