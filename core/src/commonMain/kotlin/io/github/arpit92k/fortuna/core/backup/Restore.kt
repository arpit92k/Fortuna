package io.github.arpit92k.fortuna.core.backup

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
import io.github.arpit92k.fortuna.core.domain.DatasetFormatException
import io.github.arpit92k.fortuna.core.domain.DatasetJson
import io.github.arpit92k.fortuna.core.domain.UnsupportedFormatVersionException
import io.github.arpit92k.fortuna.core.file.DamagedFileException
import io.github.arpit92k.fortuna.core.file.EncryptedFile
import io.github.arpit92k.fortuna.core.file.FileKind
import io.github.arpit92k.fortuna.core.file.NotAnEncryptedFileException
import io.github.arpit92k.fortuna.core.file.UnsupportedFileVersionException
import io.github.arpit92k.fortuna.core.keys.KeyGuard
import io.github.arpit92k.fortuna.core.keys.PinProblem
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.openWithPhrase
import io.github.arpit92k.fortuna.core.keys.pinProblem
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.core.store.DocumentStore
import kotlinx.datetime.LocalDate

/**
 * Restores a backup on a device with no data yet: a fresh install, or a new phone.
 *
 * Everything is checked before anything is written: the file, the phrase, the
 * contents and the new PIN. So a wrong phrase or a damaged file changes
 * nothing on the device. Only then is the store written, and the lock state
 * last, since the lock state marks that data exists. A store left without a
 * lock state by an earlier interrupted restore is replaced.
 *
 * The restored data counts as backed up, since it is identical to the file.
 * On success the store is left open.
 */
fun restoreFromBackup(
    file: ByteArray,
    phrase: RecoveryPhrase,
    newPin: String,
    store: DocumentStore,
    keys: KeyGuard,
    crypto: Crypto,
    today: LocalDate,
): RestoreResult {
    if (keys.exists()) return RestoreResult.AlreadySetUp
    pinProblem(newPin)?.let { return RestoreResult.InvalidPin(it) }

    val header = try {
        EncryptedFile.readHeader(file)
    } catch (e: NotAnEncryptedFileException) {
        return RestoreResult.NotABackup
    } catch (e: UnsupportedFileVersionException) {
        return RestoreResult.FromNewerApp
    } catch (e: DamagedFileException) {
        return RestoreResult.Damaged
    }
    val phraseCopy = header.lockedKey
    if (header.kind != FileKind.BACKUP || phraseCopy == null) return RestoreResult.NotABackup

    val dataKey = try {
        openWithPhrase(phraseCopy, phrase, crypto)
    } catch (e: DecryptionFailedException) {
        return RestoreResult.WrongPhrase
    }
    val dataset = try {
        DatasetJson.decode(EncryptedFile.open(file, dataKey, crypto).decodeToString())
    } catch (e: DecryptionFailedException) {
        // The phrase was right, so the file itself has been changed.
        return RestoreResult.Damaged
    } catch (e: UnsupportedFormatVersionException) {
        return RestoreResult.FromNewerApp
    } catch (e: DatasetFormatException) {
        return RestoreResult.Damaged
    }

    val pending = when (val result = keys.beginRestore(phrase, phraseCopy, dataKey, newPin)) {
        is SetupResult.Ready -> result.setup
        is SetupResult.InvalidPin -> return RestoreResult.InvalidPin(result.problem)
    }
    val settings = dataset.settings
    val backedUp = dataset.copy(
        settings = settings.copy(lastBackupRevision = settings.revision, lastBackup = settings.lastBackup ?: today),
    )

    if (store.exists()) store.deleteUnfinished()
    store.create(dataKey, backedUp)
    keys.finishSetup(pending)
    return RestoreResult.Success
}

sealed interface RestoreResult {
    /** Restored; the store is open. */
    data object Success : RestoreResult

    /** The phrase does not open this backup. */
    data object WrongPhrase : RestoreResult

    /** Not a Fortuna backup: another kind of file, or the store rather than a backup. */
    data object NotABackup : RestoreResult

    /** The file has been changed or cut short, so it cannot be trusted. */
    data object Damaged : RestoreResult

    /** Made by a newer version of the app, which this one cannot read. */
    data object FromNewerApp : RestoreResult

    data class InvalidPin(val problem: PinProblem) : RestoreResult

    /** This device already has data. Restoring over it comes in a later version. */
    data object AlreadySetUp : RestoreResult
}
