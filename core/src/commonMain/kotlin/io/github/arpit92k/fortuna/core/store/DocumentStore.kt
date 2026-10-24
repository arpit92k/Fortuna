package io.github.arpit92k.fortuna.core.store

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.DatasetJson
import io.github.arpit92k.fortuna.core.file.EncryptedFile
import io.github.arpit92k.fortuna.core.file.LockedKey
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import io.github.arpit92k.fortuna.core.ports.deleteWithLeftovers
import io.github.arpit92k.fortuna.core.ports.finishInterruptedReplace
import io.github.arpit92k.fortuna.core.ports.replaceSafely
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * The encrypted store: the single source of truth for everything recorded.
 *
 * While unlocked, it holds the data key and the whole dataset in memory, and
 * exposes the dataset as a flow for the screens to observe. Every change is
 * written as a complete new file and moved into place, so a change is saved
 * completely or not at all. Locking forgets the key and the dataset.
 *
 * A write replaces the file through `replaceSafely`, so an interruption at
 * any point leaves the old dataset or the new one, and [recover] tidies up on
 * the next start. See SafeReplace.kt for how.
 *
 * Calls must not overlap: the session (step 13) makes them one at a time.
 */
class DocumentStore(private val files: PrivateFiles, private val crypto: Crypto) {
    private val state = MutableStateFlow<Dataset?>(null)
    private var dataKey: ByteArray? = null

    /** The dataset while the store is open, and null while it is locked. */
    val dataset: StateFlow<Dataset?> = state.asStateFlow()

    val isOpen: Boolean get() = dataKey != null

    /** Tidies up after an interrupted write. Call once on start, before anything else. */
    fun recover() = files.finishInterruptedReplace(MAIN)

    /** Whether a store has been created on this device. */
    fun exists(): Boolean = files.exists(MAIN)

    /** Creates the store at setup with its first dataset, and leaves it open. */
    fun create(key: ByteArray, initial: Dataset) {
        check(!exists()) { "A store already exists on this device" }
        val copy = key.copyOf()
        write(initial, copy)
        dataKey = copy
    }

    /**
     * Opens the store with the data key and loads the dataset.
     *
     * @throws io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException if the key is wrong or the file has been changed.
     * @throws io.github.arpit92k.fortuna.core.domain.DatasetFormatException if the contents are not a valid dataset.
     */
    fun open(key: ByteArray) {
        val contents = EncryptedFile.open(files.read(MAIN), key, crypto)
        state.value = DatasetJson.decode(contents.decodeToString())
        dataKey = key.copyOf()
    }

    /**
     * Applies a recording rule. If the rule succeeds, the revision rises by one
     * and the result is saved before it is returned; this is the only place
     * the revision is raised. If the rule refuses, nothing is written.
     */
    fun apply(rule: (Dataset) -> RecordingResult): RecordingResult {
        val current = requireOpen()
        return when (val result = rule(current)) {
            is RecordingResult.Failure -> result
            is RecordingResult.Success -> {
                val changed = result.dataset
                val saved = changed.copy(settings = changed.settings.copy(revision = current.settings.revision + 1))
                write(saved, dataKey!!)
                RecordingResult.Success(saved)
            }
        }
    }

    /**
     * Saves a bookkeeping change, such as the record of a backup, without
     * raising the revision: it is not a change to what the user recorded.
     */
    fun saveBookkeeping(change: (Dataset) -> Dataset): Dataset {
        val current = requireOpen()
        val changed = change(current)
        check(changed.settings.revision == current.settings.revision) { "Bookkeeping must not change the revision" }
        write(changed, dataKey!!)
        return changed
    }

    /**
     * Makes a backup file of the current dataset, without asking for the
     * recovery phrase: it is encrypted with the data key, and [phraseCopy],
     * the data key locked by the phrase, goes in its header so that the phrase
     * opens it on any device.
     *
     * The file already records itself as the latest backup, so restored data
     * counts as backed up. The store records it only when [confirmBackupSaved]
     * is called, once the file has been saved where the user chose.
     */
    fun prepareBackup(phraseCopy: LockedKey, today: LocalDate): PreparedBackup {
        val current = requireOpen()
        val recorded = current.copy(
            settings = current.settings.copy(lastBackupRevision = current.settings.revision, lastBackup = today),
        )
        val file = EncryptedFile.seal(DatasetJson.encode(recorded).encodeToByteArray(), dataKey!!, crypto, phraseCopy)
        return PreparedBackup(file, current.settings.revision, today)
    }

    /**
     * Records that [backup] was saved, without raising the revision. Returns
     * false, and records nothing, if the data has changed since the backup was
     * made, because the backup would not hold that change.
     */
    fun confirmBackupSaved(backup: PreparedBackup): Boolean {
        val current = requireOpen()
        if (current.settings.revision != backup.revision) return false
        saveBookkeeping { it.copy(settings = it.settings.copy(lastBackupRevision = backup.revision, lastBackup = backup.date)) }
        return true
    }

    /**
     * Deletes the store from the device. Only for a store left without a lock
     * state by an interrupted setup or restore, which nothing can open.
     */
    fun deleteUnfinished() {
        lock()
        files.deleteWithLeftovers(MAIN)
    }

    /** Forgets the data key and the dataset. The store stays on disk, encrypted. */
    fun lock() {
        dataKey?.fill(0)
        dataKey = null
        state.value = null
    }

    private fun requireOpen(): Dataset = checkNotNull(state.value.takeIf { isOpen }) { "The store is locked" }

    private fun write(dataset: Dataset, key: ByteArray) {
        val sealed = EncryptedFile.seal(DatasetJson.encode(dataset).encodeToByteArray(), key, crypto)
        files.replaceSafely(MAIN, sealed)
        state.value = dataset
    }

    companion object {
        /** The store itself. */
        const val MAIN: String = "fortuna.store"

        /** A complete new copy, waiting to be moved onto [MAIN]. */
        const val NEW: String = "$MAIN.new"

        /** A new copy being written, which may be incomplete. */
        const val PARTIAL: String = "$MAIN.partial"
    }
}

/** A backup file ready to save, and what the store records once it has been saved. */
class PreparedBackup internal constructor(
    /** The encrypted backup file. */
    val file: ByteArray,
    internal val revision: Long,
    val date: LocalDate,
)
