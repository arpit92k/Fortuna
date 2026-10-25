package io.github.arpit92k.fortuna.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.backup.RestoreResult
import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.platform.DocumentFiles
import io.github.arpit92k.fortuna.platform.FileTooLargeException
import io.github.arpit92k.fortuna.ui.PhraseCheck
import io.github.arpit92k.fortuna.ui.PhraseMessage
import io.github.arpit92k.fortuna.ui.checkPhrase
import io.github.arpit92k.fortuna.ui.emptyPhraseWords
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where the user is in restoring from a backup (UF-13). */
sealed interface RestoreStep {
    /** S-06: what restoring does, and the file to choose. */
    data class ChooseFile(val problem: RestoreProblem? = null, val reading: Boolean = false) : RestoreStep

    /** S-06: the twelve words that open the file. */
    data class Phrase(val words: List<String> = emptyPhraseWords(), val message: PhraseMessage? = null) : RestoreStep

    /** S-02, for the restored data: the PIN, typed twice. */
    data class NewPin(val pad: SetupStep.CreatePin = SetupStep.CreatePin()) : RestoreStep
}

/** Why the chosen file was not restored. */
enum class RestoreProblem {
    /** The file could not be read. */
    CANNOT_READ,

    /** Not a Fortuna backup at all. */
    NOT_A_BACKUP,

    /** A Fortuna backup, but damaged. */
    DAMAGED,

    /** Made by a newer version of the app. */
    FROM_NEWER_APP,
}

/**
 * Restores a backup on a phone with no data: the file, then the phrase, then
 * a new PIN. The core restores only once all three are in hand, and a wrong
 * phrase or a damaged file leaves the phone exactly as it was: still with no
 * data, on the Welcome screen's restore.
 */
class RestoreViewModel(
    private val core: Core,
    private val files: DocumentFiles,
    private val crypto: Crypto = PlatformCrypto,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableStep = MutableStateFlow<RestoreStep?>(null)

    /** Null while restoring is not under way. */
    val step: StateFlow<RestoreStep?> = mutableStep.asStateFlow()

    private var file: ByteArray? = null
    private var phrase: RecoveryPhrase? = null
    private var words: List<String> = emptyPhraseWords()

    /** "Restore from a backup" on S-01. */
    fun begin() {
        forget()
        mutableStep.value = RestoreStep.ChooseFile()
    }

    /** The file the picker returned, or null if the user closed it. */
    fun fileChosen(location: String?) {
        val step = mutableStep.value as? RestoreStep.ChooseFile ?: return
        if (location == null || step.reading) return
        mutableStep.value = RestoreStep.ChooseFile(reading = true)
        viewModelScope.launch {
            var problem: RestoreProblem? = null
            val read = try {
                withContext(io) { files.read(location, MAX_BACKUP_BYTES) }
            } catch (e: FileTooLargeException) {
                problem = RestoreProblem.NOT_A_BACKUP
                null
            } catch (e: IOException) {
                problem = RestoreProblem.CANNOT_READ
                null
            } catch (e: SecurityException) {
                // The picker's permission was withdrawn before the file was read.
                problem = RestoreProblem.CANNOT_READ
                null
            }
            // Left meanwhile.
            if (mutableStep.value != RestoreStep.ChooseFile(reading = true)) return@launch
            if (read == null) {
                mutableStep.value = RestoreStep.ChooseFile(problem)
            } else {
                file = read
                mutableStep.value = RestoreStep.Phrase()
            }
        }
    }

    fun words(words: List<String>) {
        mutableStep.update { if (it is RestoreStep.Phrase) it.copy(words = words, message = null) else it }
    }

    /** Checks the words as typed, then asks for the PIN. */
    fun phraseDone() {
        val step = mutableStep.value as? RestoreStep.Phrase ?: return
        when (val check = checkPhrase(step.words, crypto)) {
            is PhraseCheck.Ready -> {
                phrase = check.phrase
                words = step.words
                mutableStep.value = RestoreStep.NewPin()
            }
            is PhraseCheck.Problem -> mutableStep.value = step.copy(message = check.message)
        }
    }

    fun digit(digit: Char) = updatePad { it.withDigit(digit) }

    fun deleteDigit() = updatePad { it.withoutDigit() }

    fun pinOk() {
        val step = mutableStep.value as? RestoreStep.NewPin ?: return
        if (step.pad.working) return
        val file = file ?: return
        val phrase = phrase ?: return
        when (val ok = step.pad.ok()) {
            is NewPinOk.Again -> mutableStep.value = RestoreStep.NewPin(ok.pad)
            is NewPinOk.Chosen -> {
                mutableStep.value = RestoreStep.NewPin(step.pad.copy(working = true))
                viewModelScope.launch {
                    val result = try {
                        core.run { restore(file, phrase, ok.pin) }
                    } catch (e: IllegalStateException) {
                        // The phone has data after all; restoring is not for it.
                        RestoreResult.AlreadySetUp
                    }
                    when (result) {
                        // The session is unlocked with the restored data, and the app moves on.
                        RestoreResult.Success, RestoreResult.AlreadySetUp -> forget()
                        RestoreResult.WrongPhrase -> mutableStep.value = RestoreStep.Phrase(words, PhraseMessage.WrongPhrase)
                        RestoreResult.NotABackup -> chooseAgain(RestoreProblem.NOT_A_BACKUP)
                        RestoreResult.Damaged -> chooseAgain(RestoreProblem.DAMAGED)
                        RestoreResult.FromNewerApp -> chooseAgain(RestoreProblem.FROM_NEWER_APP)
                        // The pad only types digits, and OK needs six.
                        is RestoreResult.InvalidPin -> mutableStep.value = RestoreStep.NewPin(SetupStep.CreatePin(problem = PinMessage.TOO_SHORT))
                    }
                }
            }
        }
    }

    /** Back one step; from choosing the file, back to S-01. */
    fun back() {
        when (val step = mutableStep.value) {
            is RestoreStep.ChooseFile -> forget()
            is RestoreStep.Phrase -> {
                file = null
                words = emptyPhraseWords()
                mutableStep.value = RestoreStep.ChooseFile()
            }
            is RestoreStep.NewPin -> when {
                step.pad.working -> {}
                step.pad.first != null -> mutableStep.value = RestoreStep.NewPin()
                else -> mutableStep.value = RestoreStep.Phrase(words)
            }
            null -> {}
        }
    }

    private fun chooseAgain(problem: RestoreProblem) {
        file = null
        phrase = null
        words = emptyPhraseWords()
        mutableStep.value = RestoreStep.ChooseFile(problem)
    }

    private fun updatePad(change: (SetupStep.CreatePin) -> SetupStep.CreatePin) {
        mutableStep.update { if (it is RestoreStep.NewPin && !it.pad.working) RestoreStep.NewPin(change(it.pad)) else it }
    }

    private fun forget() {
        file = null
        phrase = null
        words = emptyPhraseWords()
        mutableStep.value = null
    }

    companion object {
        /** Far more than years of monthly values take; a larger file is not a backup. */
        const val MAX_BACKUP_BYTES: Int = 64 * 1024 * 1024
    }
}
