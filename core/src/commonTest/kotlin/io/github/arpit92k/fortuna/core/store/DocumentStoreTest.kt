package io.github.arpit92k.fortuna.core.store

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentStoreTest {
    private val crypto = PlatformCrypto
    private val pounds = Currency.forCode("GBP")!!
    private val today = LocalDate(2026, 10, 24)

    // Tests run against Okio's in-memory file system, as the plan says.
    private val fileSystem = FakeFileSystem()
    private val disk = OkioPrivateFiles(fileSystem, "/data".toPath())
    private val key = crypto.randomBytes(Crypto.KEY_SIZE)

    private val initial = Dataset.new()

    /** A store as the app would make it on start: recover first. */
    private fun startStore(files: PrivateFiles = disk) = DocumentStore(files, crypto).also { it.recover() }

    private fun created(): DocumentStore = startStore().also { it.create(key, initial) }

    private val chooseBase: (Dataset) -> RecordingResult = { it.chooseBaseCurrency(pounds) }

    private fun addSource(name: String): (Dataset) -> RecordingResult =
        { it.addSource(name, CategoryId(1), today, Money(100_000, pounds), today) }

    // ---- Creating and opening ----

    @Test
    fun aNewStoreIsCreatedOpen() {
        val store = startStore()
        assertFalse(store.exists())
        store.create(key, initial)
        assertTrue(store.exists())
        assertTrue(store.isOpen)
        assertEquals(initial, store.dataset.value)
    }

    @Test
    fun theStoreCanBeCreatedOnlyOnce() {
        created()
        assertFailsWith<IllegalStateException> { startStore().create(key, initial) }
    }

    @Test
    fun theFileOnDiskIsEncrypted() {
        created().apply(chooseBase)
        val onDisk = disk.read(DocumentStore.MAIN).decodeToString(throwOnInvalidSequence = false)
        assertFalse("formatVersion" in onDisk || "GBP" in onDisk || "Cash" in onDisk, "no plain text on disk")
    }

    // From the plan: a change is still there after the store is closed and reopened.
    @Test
    fun aChangeIsStillThereAfterReopening() {
        val store = created()
        store.apply(chooseBase)
        store.apply(addSource("Current account"))
        val expected = store.dataset.value

        val reopened = startStore()
        reopened.open(key)
        assertEquals(expected, reopened.dataset.value)
        assertEquals("Current account", reopened.dataset.value!!.sources.single().name)
    }

    @Test
    fun aWrongKeyDoesNotOpenTheStore() {
        created()
        val store = startStore()
        assertFailsWith<DecryptionFailedException> { store.open(crypto.randomBytes(Crypto.KEY_SIZE)) }
        assertFalse(store.isOpen)
        assertNull(store.dataset.value)
    }

    // ---- Changes ----

    // From the plan: each saved change raises the revision by one.
    @Test
    fun eachSavedChangeRaisesTheRevisionByOne() {
        val store = created()
        assertEquals(0, store.dataset.value!!.settings.revision)
        val first = assertIs<RecordingResult.Success>(store.apply(chooseBase))
        assertEquals(1, first.dataset.settings.revision)
        store.apply(addSource("A"))
        store.apply(addSource("B"))
        assertEquals(3, store.dataset.value!!.settings.revision)
    }

    // From the plan: a rule that returns an error leaves the file and the revision untouched.
    @Test
    fun aRefusedRuleLeavesTheFileAndRevisionUntouched() {
        val store = created()
        store.apply(chooseBase)
        val before = disk.read(DocumentStore.MAIN)
        val refused = store.apply(chooseBase)
        assertEquals(RecordingError.BaseCurrencyAlreadyChosen, assertIs<RecordingResult.Failure>(refused).error)
        assertContentEquals(before, disk.read(DocumentStore.MAIN))
        assertEquals(1, store.dataset.value!!.settings.revision)
    }

    @Test
    fun theFlowShowsEachSavedChange() {
        val store = created()
        store.apply(chooseBase)
        assertEquals(pounds, store.dataset.value!!.baseCurrency)
    }

    @Test
    fun bookkeepingIsSavedWithoutRaisingTheRevision() {
        val store = created()
        store.apply(chooseBase)
        val saved = store.saveBookkeeping { it.copy(settings = it.settings.copy(lastBackupRevision = 1, lastBackup = today)) }
        assertEquals(1, saved.settings.revision)
        assertEquals(1L, saved.settings.lastBackupRevision)

        val reopened = startStore().also { it.open(key) }
        assertEquals(today, reopened.dataset.value!!.settings.lastBackup)
        assertFailsWith<IllegalStateException> { store.saveBookkeeping { it.copy(settings = it.settings.copy(revision = 5)) } }
    }

    // ---- Locking ----

    // From the plan: after locking, the store holds no data.
    @Test
    fun afterLockingTheStoreHoldsNoData() {
        val store = created()
        store.apply(chooseBase)
        store.lock()
        assertFalse(store.isOpen)
        assertNull(store.dataset.value)
        assertFailsWith<IllegalStateException> { store.apply(addSource("A")) }
        assertFailsWith<IllegalStateException> { store.saveBookkeeping { it } }

        store.open(key)
        assertEquals(pounds, store.dataset.value!!.baseCurrency, "unlocking again loads the saved data")
    }

    @Test
    fun theStoreKeepsItsOwnCopyOfTheKey() {
        val callerKey = key.copyOf()
        val store = startStore()
        store.create(callerKey, initial)
        callerKey.fill(0)
        store.apply(chooseBase)
        startStore().open(key)
    }

    // ---- Interrupted writes ----

    private class Crash : RuntimeException("simulated crash")

    /** Files that crash at a chosen point, as if the phone lost power there. */
    private class CrashingFiles(private val delegate: PrivateFiles) : PrivateFiles by delegate {
        var crashWhen: (operation: String, name: String) -> Boolean = { _, _ -> false }

        override fun write(name: String, contents: ByteArray) {
            if (crashWhen("write", name)) {
                delegate.write(name, contents.copyOf(contents.size / 2))
                throw Crash()
            }
            delegate.write(name, contents)
        }

        override fun move(from: String, to: String) {
            if (crashWhen("move", from)) throw Crash()
            delegate.move(from, to)
        }
    }

    /** Saves one change on a store whose files crash at the given point. */
    private fun crashWhileSaving(crashWhen: (String, String) -> Boolean) {
        val files = CrashingFiles(disk)
        val store = DocumentStore(files, crypto).also { it.recover() }
        store.open(key)
        files.crashWhen = crashWhen
        assertFailsWith<Crash> { store.apply(addSource("Interrupted")) }
    }

    private fun reopenedAfterRestart(): Dataset = startStore().also { it.open(key) }.dataset.value!!

    // From the plan: a write interrupted before the move leaves the old data readable.
    @Test
    fun aWriteInterruptedWhileWritingLeavesTheOldData() {
        created().apply(chooseBase)
        crashWhileSaving { operation, _ -> operation == "write" }
        assertTrue(disk.exists(DocumentStore.PARTIAL), "half a file was left behind")

        val after = reopenedAfterRestart()
        assertTrue(after.sources.isEmpty())
        assertEquals(1, after.settings.revision)
        assertFalse(disk.exists(DocumentStore.PARTIAL), "and it was thrown away")
    }

    @Test
    fun aWriteInterruptedBeforeTheFirstMoveLeavesTheOldData() {
        created().apply(chooseBase)
        crashWhileSaving { operation, name -> operation == "move" && name == DocumentStore.PARTIAL }
        assertTrue(reopenedAfterRestart().sources.isEmpty())
    }

    // From the plan: a write interrupted after the move is recovered on start.
    @Test
    fun aWriteInterruptedAfterTheFirstMoveIsRecoveredOnStart() {
        created().apply(chooseBase)
        crashWhileSaving { operation, name -> operation == "move" && name == DocumentStore.NEW }
        assertTrue(disk.exists(DocumentStore.NEW), "a complete new copy was waiting")

        val after = reopenedAfterRestart()
        assertEquals("Interrupted", after.sources.single().name)
        assertEquals(2, after.settings.revision)
        assertFalse(disk.exists(DocumentStore.NEW))
    }

    @Test
    fun aNewCopyLeftWithoutTheOldFileIsRecovered() {
        // Some platforms remove the old file before failing to rename the new one.
        created().apply(chooseBase)
        crashWhileSaving { operation, name -> operation == "move" && name == DocumentStore.NEW }
        disk.delete(DocumentStore.MAIN)

        assertEquals("Interrupted", reopenedAfterRestart().sources.single().name)
    }

    @Test
    fun aSetupInterruptedBeforeTheFirstFileLeavesNoStore() {
        val files = CrashingFiles(disk)
        files.crashWhen = { operation, _ -> operation == "write" }
        assertFailsWith<Crash> { DocumentStore(files, crypto).create(key, initial) }

        assertFalse(startStore().exists(), "setup can simply start again")
    }

    @Test
    fun recoveringWithNothingToRecoverChangesNothing() {
        created().apply(chooseBase)
        val before = disk.read(DocumentStore.MAIN)
        startStore()
        assertContentEquals(before, disk.read(DocumentStore.MAIN))
    }

    // ---- The Okio port ----

    @Test
    fun privateFilesAreNamedNotGivenPaths() {
        for (name in listOf("", "../escape", "a/b", ".", "..")) {
            assertFailsWith<IllegalArgumentException>(name) { disk.write(name, ByteArray(1)) }
        }
    }

    @Test
    fun aWriteReplacesTheWholeFile() {
        disk.write("f", ByteArray(10) { 1 })
        disk.write("f", ByteArray(3) { 2 })
        assertContentEquals(ByteArray(3) { 2 }, disk.read("f"))
        disk.delete("f")
        disk.delete("f")
        assertFalse(disk.exists("f"))
    }
}
