package io.github.arpit92k.fortuna.setup

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.FakeDocumentFiles
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.setUpLocked
import io.github.arpit92k.fortuna.settings.BackupMessage
import io.github.arpit92k.fortuna.settings.BackupViewModel
import io.github.arpit92k.fortuna.someoneElsesPhrase
import io.github.arpit92k.fortuna.ui.PhraseMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S-17 Backup on one phone, and S-06 Restore on another, through the real
 * core: the file goes from one to the other, and only the phrase opens it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val files = FakeDocumentFiles()
    private val pin = "246810"
    private val place = "content://downloads/fortuna-backup"
    private val oldPhone = TestPhone()
    private val newPhone = TestPhone()
    private lateinit var words: List<String>
    private lateinit var old: Core
    private lateinit var backup: BackupViewModel
    private lateinit var fresh: Core
    private lateinit var restore: RestoreViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        words = oldPhone.setUpLocked(pin)
        old = oldPhone.start(dispatcher)
        backup = BackupViewModel(old, files, io = dispatcher)
        fresh = newPhone.start(dispatcher)
        restore = RestoreViewModel(fresh, files, io = dispatcher)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private suspend fun unlockOld() = old.run { unlock(pin) }

    private suspend fun addSavings() {
        val result = old.run {
            record { data, today -> data.addSource("Savings", CategoryId(1), LocalDate(2026, 9, 30), Money(1_250_00, Currency.forCode("GBP")!!), today) }
        }
        assertIs<RecordingResult.Success>(result)
    }

    private fun TestScope.saveBackup() {
        backup.save(place)
        advanceUntilIdle()
    }

    private fun TestScope.restoreWith(location: String, phrase: List<String>, newPin: String = "135791") {
        advanceUntilIdle()
        restore.begin()
        restore.fileChosen(location)
        advanceUntilIdle()
        if (restore.step.value !is RestoreStep.Phrase) return
        restore.words(phrase)
        restore.phraseDone()
        repeat(2) {
            newPin.forEach(restore::digit)
            restore.pinOk()
        }
        advanceUntilIdle()
    }

    private suspend fun assertFreshPhoneUntouched() {
        assertEquals(SessionState.NoData, fresh.state.value)
        // Starting the app again finds no data either: nothing was left behind.
        assertEquals(SessionState.NoData, newPhone.start(dispatcher).state.value)
    }

    @Test
    fun aBackupSavedOnOnePhoneRestoresOnAnother() = runTest(dispatcher) {
        unlockOld()
        addSavings()
        advanceUntilIdle()
        assertNull(backup.ui.value.lastBackup, "no backup yet")

        saveBackup()
        assertEquals(BackupMessage.SAVED, backup.ui.value.message)
        assertEquals(LocalDate(2026, 10, 24), backup.ui.value.lastBackup)
        assertTrue(backup.ui.value.upToDate)
        assertNotNull(files.saved[place])

        restoreWith(place, words)
        assertEquals(SessionState.Unlocked, fresh.state.value)
        assertNull(restore.step.value, "the file and words are forgotten once used")
        val restored = fresh.dataset.value!!
        assertEquals("GBP", restored.baseCurrency?.code)
        assertEquals(listOf("Savings"), restored.sources.map { it.name })

        // The restored data opens with the PIN chosen while restoring.
        fresh.run { lock() }
        assertIs<io.github.arpit92k.fortuna.core.keys.UnlockResult.Success>(fresh.run { unlock("135791") })
    }

    @Test
    fun aChangeAfterTheBackupIsShown() = runTest(dispatcher) {
        unlockOld()
        saveBackup()
        assertTrue(backup.ui.value.upToDate)
        addSavings()
        advanceUntilIdle()
        assertFalse(backup.ui.value.upToDate, "the backup no longer holds everything")
        assertEquals(LocalDate(2026, 10, 24), backup.ui.value.lastBackup)
    }

    @Test
    fun aPlaceThatCannotBeWrittenSavesNothing() = runTest(dispatcher) {
        unlockOld()
        files.failing += place
        saveBackup()
        assertEquals(BackupMessage.NOT_SAVED, backup.ui.value.message)
        assertNull(backup.ui.value.lastBackup, "not counted as a backup")
    }

    @Test
    fun lockingWhileThePickerIsOpenSavesOnUnlocking() = runTest(dispatcher) {
        unlockOld()
        advanceUntilIdle()
        old.run { lock() }
        advanceUntilIdle()
        saveBackup()
        assertNull(files.saved[place], "nothing can be made while locked")
        unlockOld()
        advanceUntilIdle()
        assertEquals(BackupMessage.SAVED, backup.ui.value.message)
        assertNotNull(files.saved[place])
    }

    @Test
    fun aWrongPhraseLeavesTheFreshPhoneUnchanged() = runTest(dispatcher) {
        unlockOld()
        saveBackup()
        restoreWith(place, someoneElsesPhrase)
        assertEquals(RestoreStep.Phrase(someoneElsesPhrase, PhraseMessage.WrongPhrase), restore.step.value)
        assertFreshPhoneUntouched()
    }

    @Test
    fun aDamagedBackupLeavesTheFreshPhoneUnchanged() = runTest(dispatcher) {
        unlockOld()
        saveBackup()
        val file = files.saved.getValue(place)
        // The last byte belongs to the check that seals the contents.
        file[file.lastIndex] = (file[file.lastIndex] + 1).toByte()
        restoreWith(place, words)
        assertEquals(RestoreStep.ChooseFile(RestoreProblem.DAMAGED), restore.step.value)
        assertFreshPhoneUntouched()
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() = runTest(dispatcher) {
        files.saved["content://photo"] = "not a backup at all".encodeToByteArray()
        restoreWith("content://photo", words)
        assertEquals(RestoreStep.ChooseFile(RestoreProblem.NOT_A_BACKUP), restore.step.value)
        assertFreshPhoneUntouched()
    }

    @Test
    fun aFileThatCannotBeReadIsSaidSo() = runTest(dispatcher) {
        restoreWith("content://gone", words)
        assertEquals(RestoreStep.ChooseFile(RestoreProblem.CANNOT_READ), restore.step.value)
    }

    @Test
    fun closingThePickerStaysOnTheRestoreScreen() = runTest(dispatcher) {
        restore.begin()
        restore.fileChosen(null)
        assertEquals(RestoreStep.ChooseFile(), restore.step.value)
        restore.back()
        assertNull(restore.step.value, "back to the welcome screen")
    }

    @Test
    fun backFromThePinKeepsTheWords() = runTest(dispatcher) {
        unlockOld()
        saveBackup()
        restore.begin()
        restore.fileChosen(place)
        advanceUntilIdle()
        restore.words(words)
        restore.phraseDone()
        assertIs<RestoreStep.NewPin>(restore.step.value)
        restore.back()
        assertEquals(RestoreStep.Phrase(words), restore.step.value)
        restore.back()
        assertEquals(RestoreStep.ChooseFile(), restore.step.value, "the file is chosen again")
    }
}
