package io.github.arpit92k.fortuna.core.backup

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.DatasetJson
import io.github.arpit92k.fortuna.core.domain.FormatFixtures
import io.github.arpit92k.fortuna.core.file.EncryptedFile
import io.github.arpit92k.fortuna.core.keys.FakeClock
import io.github.arpit92k.fortuna.core.keys.FakeDeviceKey
import io.github.arpit92k.fortuna.core.keys.KeyGuard
import io.github.arpit92k.fortuna.core.keys.PinProblem
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.phrase.PhraseParseResult
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import io.github.arpit92k.fortuna.core.store.DocumentStore
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupAndRestoreTest {
    private val crypto = PlatformCrypto
    private val clock = FakeClock()
    private val today = LocalDate(2026, 10, 24)
    private val pounds = Currency.forCode("GBP")!!

    /** A stand-in device: its own disk and its own device key. */
    private inner class Device {
        val disk = OkioPrivateFiles(FakeFileSystem(), "/data".toPath())
        val keys = KeyGuard(disk, crypto, FakeDeviceKey(), clock, pinIterations = { 1_000 }, phraseIterations = 1_000)
            .also { it.recover() }
        val store = DocumentStore(disk, crypto).also { it.recover() }

        fun hasNoData() = !disk.exists(KeyGuard.LOCK_FILE) && !disk.exists(DocumentStore.MAIN)

        fun restore(file: ByteArray, phrase: RecoveryPhrase, pin: String = "975310") =
            restoreFromBackup(file, phrase, pin, store, keys, crypto, today)
    }

    private val first = Device()
    private val second = Device()

    /** Sets up [device] with a pounds base and one source, and returns its recovery phrase. */
    private fun setUp(device: Device): RecoveryPhrase {
        val setup = assertIs<SetupResult.Ready>(device.keys.beginSetup("246810")).setup
        device.store.create(setup.dataKey, Dataset.new())
        device.keys.finishSetup(setup)
        device.store.apply { it.chooseBaseCurrency(pounds) }
        device.store.apply { it.addSource("Current account", CategoryId(1), today, Money(250_000, pounds), today) }
        return setup.phrase
    }

    private fun backUp(device: Device): ByteArray {
        val backup = device.store.prepareBackup(device.keys.phraseCopy()!!, today)
        assertTrue(device.store.confirmBackupSaved(backup))
        return backup.file
    }

    // ---- Backing up ----

    // From the plan: back up on one stand-in device and restore on another; the datasets are equal.
    @Test
    fun aBackupFromOneDeviceRestoresOnAnother() {
        val phrase = setUp(first)
        val file = backUp(first)

        assertEquals(RestoreResult.Success, second.restore(file, phrase))
        assertEquals(first.store.dataset.value, second.store.dataset.value)
        assertEquals("Current account", second.store.dataset.value!!.sources.single().name)

        // The new PIN opens the restored data on the second device.
        val unlocked = assertIs<UnlockResult.Success>(second.keys.unlock("975310"))
        second.store.lock()
        second.store.open(unlocked.dataKey)
        assertEquals(first.store.dataset.value, second.store.dataset.value)
    }

    @Test
    fun makingABackupNeedsNoPhrase() {
        setUp(first)
        // The phrase's copy of the key comes from the lock state, already locked.
        val backup = first.store.prepareBackup(first.keys.phraseCopy()!!, today)
        assertContentEquals(first.keys.phraseCopy()!!.sealedKey, EncryptedFile.readHeader(backup.file).lockedKey!!.sealedKey)
    }

    // From the plan: after a backup the two revisions are equal; a change later the same day passes it.
    @Test
    fun aBackupRecordsItsRevisionAndALaterChangePassesIt() {
        setUp(first)
        backUp(first)
        val settings = first.store.dataset.value!!.settings
        assertEquals(settings.revision, settings.lastBackupRevision)
        assertEquals(today, settings.lastBackup)

        first.store.apply { it.addSource("Savings", CategoryId(1), today, Money(1, pounds), today) }
        val later = first.store.dataset.value!!.settings
        assertEquals(later.lastBackupRevision!! + 1, later.revision, "same day, yet the backup is now out of date")
    }

    @Test
    fun aBackupIsRecordedOnlyOnceItHasBeenSaved() {
        setUp(first)
        val backup = first.store.prepareBackup(first.keys.phraseCopy()!!, today)
        assertNull(first.store.dataset.value!!.settings.lastBackupRevision, "not recorded before it is saved")

        first.store.apply { it.addSource("Savings", CategoryId(1), today, Money(1, pounds), today) }
        assertFalse(first.store.confirmBackupSaved(backup), "the data changed since; the backup would not hold it")
        assertNull(first.store.dataset.value!!.settings.lastBackupRevision)
    }

    @Test
    fun recordingABackupDoesNotRaiseTheRevision() {
        setUp(first)
        val before = first.store.dataset.value!!.settings.revision
        backUp(first)
        assertEquals(before, first.store.dataset.value!!.settings.revision)
    }

    @Test
    fun aBackupHoldsNoPlainText() {
        setUp(first)
        val text = backUp(first).decodeToString(throwOnInvalidSequence = false)
        assertFalse("Current account" in text || "GBP" in text || "formatVersion" in text)
    }

    // ---- Restoring ----

    // From the plan: after a restore the two revisions are equal.
    @Test
    fun restoredDataCountsAsBackedUp() {
        val phrase = setUp(first)
        second.restore(backUp(first), phrase)
        val settings = second.store.dataset.value!!.settings
        assertEquals(settings.revision, settings.lastBackupRevision)
    }

    // From the plan: a wrong phrase changes nothing on the device.
    @Test
    fun aWrongPhraseChangesNothing() {
        setUp(first)
        val file = backUp(first)
        val wrongPhrase = RecoveryPhrase.fromSecret(crypto.randomBytes(16), crypto)
        assertEquals(RestoreResult.WrongPhrase, second.restore(file, wrongPhrase))
        assertTrue(second.hasNoData())
    }

    // From the plan: a damaged file changes nothing on the device.
    @Test
    fun aDamagedFileChangesNothing() {
        val phrase = setUp(first)
        val file = backUp(first)

        val lastByteChanged = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals(RestoreResult.Damaged, second.restore(lastByteChanged, phrase))
        assertEquals(RestoreResult.Damaged, second.restore(file.copyOf(80), phrase), "cut short")

        val lockedKeyChanged = file.copyOf().also { it[50] = (it[50].toInt() xor 1).toByte() }
        assertNotEquals<RestoreResult>(RestoreResult.Success, second.restore(lockedKeyChanged, phrase))
        assertTrue(second.hasNoData())
    }

    @Test
    fun aBackupFromANewerAppIsRefused() {
        val phrase = setUp(first)
        val file = backUp(first).also { it[5] = 2 }
        assertEquals(RestoreResult.FromNewerApp, second.restore(file, phrase))
        assertTrue(second.hasNoData())
    }

    @Test
    fun onlyABackupCanBeRestored() {
        val phrase = setUp(first)
        assertEquals(RestoreResult.NotABackup, second.restore(first.disk.read(DocumentStore.MAIN), phrase), "the store itself")
        assertEquals(RestoreResult.NotABackup, second.restore("a photo".encodeToByteArray(), phrase))
        assertTrue(second.hasNoData())
    }

    @Test
    fun aDeviceWithDataCannotRestoreOverIt() {
        val phrase = setUp(first)
        val file = backUp(first)
        assertEquals(RestoreResult.AlreadySetUp, first.restore(file, phrase))
    }

    @Test
    fun theNewPinMustBeValid() {
        val phrase = setUp(first)
        assertEquals(RestoreResult.InvalidPin(PinProblem.TOO_SHORT), second.restore(backUp(first), phrase, pin = "123"))
        assertTrue(second.hasNoData())
    }

    // Decided: a store left without a lock state by an interrupted restore is replaced.
    @Test
    fun aStoreLeftByAnInterruptedRestoreIsReplaced() {
        val phrase = setUp(first)
        val file = backUp(first)
        second.store.create(crypto.randomBytes(Crypto.KEY_SIZE), Dataset.new())
        second.store.lock()
        assertFalse(second.keys.exists())

        assertEquals(RestoreResult.Success, second.restore(file, phrase))
        assertEquals(first.store.dataset.value, second.store.dataset.value)
        assertTrue(second.keys.exists())
    }

    // From the plan: a backup file kept in the repository restores with a known phrase.
    @Test
    fun theVersion1BackupFixtureRestoresWithItsPhrase() {
        val phrase = assertIs<PhraseParseResult.Success>(RecoveryPhrase.parse(BackupFixtures.PHRASE, crypto)).phrase
        assertEquals(RestoreResult.Success, second.restore(BackupFixtures.BACKUP_V1, phrase))

        val expected = DatasetJson.decode(
            FormatFixtures.DATASET_V1.replace("\"lastBackupRevision\": 11", "\"lastBackupRevision\": 12"),
        )
        assertEquals(expected, second.store.dataset.value)
        assertContentEquals(ByteArray(32) { (0x20 + it).toByte() }, assertIs<UnlockResult.Success>(second.keys.unlock("975310")).dataKey)
        assertEquals(100_000, EncryptedFile.readHeader(BackupFixtures.BACKUP_V1).lockedKey!!.derivation.iterations)
    }
}
