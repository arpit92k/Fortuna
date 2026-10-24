package io.github.arpit92k.fortuna.core.store

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.DatasetJson
import io.github.arpit92k.fortuna.core.file.EncryptedFile
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The encrypted store: the single source of truth for everything recorded.
 *
 * While unlocked, it holds the data key and the whole dataset in memory, and
 * exposes the dataset as a flow for the screens to observe. Every change is
 * written as a complete new file and moved into place, so a change is saved
 * completely or not at all. Locking forgets the key and the dataset.
 *
 * A write goes through three names, so an interruption at any point can be
 * tidied on the next start by [recover]:
 *
 * 1. the new file is written to [PARTIAL] and forced to disk;
 * 2. [PARTIAL] is moved to [NEW], marking it complete;
 * 3. [NEW] is moved onto [MAIN].
 *
 * A [PARTIAL] file may be incomplete, so it is thrown away. A [NEW] file is
 * always complete and is the latest change, so it is moved into place. That
 * also covers platforms where the last move removes the old file before
 * failing to rename the new one.
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
    fun recover() {
        files.delete(PARTIAL)
        if (files.exists(NEW)) files.move(NEW, MAIN)
    }

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

    /** Forgets the data key and the dataset. The store stays on disk, encrypted. */
    fun lock() {
        dataKey?.fill(0)
        dataKey = null
        state.value = null
    }

    private fun requireOpen(): Dataset = checkNotNull(state.value.takeIf { isOpen }) { "The store is locked" }

    private fun write(dataset: Dataset, key: ByteArray) {
        val sealed = EncryptedFile.seal(DatasetJson.encode(dataset).encodeToByteArray(), key, crypto)
        files.write(PARTIAL, sealed)
        files.move(PARTIAL, NEW)
        files.move(NEW, MAIN)
        state.value = dataset
    }

    companion object {
        /** The store itself. */
        const val MAIN: String = "fortuna.store"

        /** A complete new copy, waiting to be moved onto [MAIN]. */
        const val NEW: String = "fortuna.store.new"

        /** A new copy being written, which may be incomplete. */
        const val PARTIAL: String = "fortuna.store.partial"
    }
}
