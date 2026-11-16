package io.github.arpit92k.fortuna.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.importing.IMPORT_TEMPLATE
import io.github.arpit92k.fortuna.core.importing.ImportCheck
import io.github.arpit92k.fortuna.core.importing.ImportPlan
import io.github.arpit92k.fortuna.core.importing.ImportProblem
import io.github.arpit92k.fortuna.core.importing.applyImport
import io.github.arpit92k.fortuna.core.importing.checkImport
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.platform.DocumentFiles
import io.github.arpit92k.fortuna.platform.FileTooLargeException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where the user is in importing history (UF-15). */
sealed interface ImportStep {
    /** What importing does, the template to save, and the file to choose. */
    data class Start(val message: ImportMessage? = null, val working: Boolean = false) : ImportStep

    /** The file did not follow the template; every problem, by its line. */
    data class Problems(val problems: List<ImportProblem>) : ImportStep

    /** The preview, with the decisions it needs: replace or skip, and any rates. */
    data class Preview(
        val plan: ImportPlan,
        val replace: Boolean = false,
        val rates: Map<Currency, Rate> = emptyMap(),
        val working: Boolean = false,
        val failed: Boolean = false,
    ) : ImportStep {
        /** Every rate the import needs has been given. */
        val ready: Boolean get() = plan.ratesNeeded.keys.all { it in rates }
    }

    /** Imported; the user is told to delete the file, which the app does not protect. */
    data class Done(val values: Int, val accounts: Int) : ImportStep
}

enum class ImportMessage { TEMPLATE_SAVED, TEMPLATE_NOT_SAVED, CANNOT_READ }

/**
 * Import history from a spreadsheet (UF-15): save the empty template, pick
 * the filled-in file, see the preview and decide, then import. Nothing is
 * written until the user confirms, and the import goes in whole or not at all.
 */
class ImportViewModel(
    private val core: Core,
    private val files: DocumentFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableStep = MutableStateFlow<ImportStep>(ImportStep.Start())
    val step: StateFlow<ImportStep> = mutableStep.asStateFlow()

    val templateName: String = "Fortuna import template.csv"

    /** Writes the template where the picker said, or does nothing if it was closed. */
    fun saveTemplate(location: String?) {
        if (location == null) return
        viewModelScope.launch {
            val saved = runCatching { withContext(io) { files.write(location, IMPORT_TEMPLATE.encodeToByteArray()) } }.isSuccess
            mutableStep.value = ImportStep.Start(if (saved) ImportMessage.TEMPLATE_SAVED else ImportMessage.TEMPLATE_NOT_SAVED)
        }
    }

    /** Reads the file the picker returned, or does nothing if it was closed. */
    fun fileChosen(location: String?) {
        if (location == null) return
        mutableStep.value = ImportStep.Start(working = true)
        viewModelScope.launch {
            val text = try {
                withContext(io) { files.read(location, MAX_IMPORT_BYTES) }.decodeToString()
            } catch (e: IOException) {
                null
            } catch (e: FileTooLargeException) {
                null
            } catch (e: SecurityException) {
                null
            }
            if (text == null) {
                mutableStep.value = ImportStep.Start(ImportMessage.CANNOT_READ)
                return@launch
            }
            val check = core.run { read { data, today -> data.checkImport(text, today) } }
            mutableStep.value = when (check) {
                is ImportCheck.Ready -> ImportStep.Preview(check.plan)
                is ImportCheck.Problems -> ImportStep.Problems(check.problems)
            }
        }
    }

    fun replace(replace: Boolean) = updatePreview { it.copy(replace = replace) }

    fun rate(currency: Currency, rate: Rate) = updatePreview { it.copy(rates = it.rates + (currency to rate)) }

    /** Imports what the preview shows, as decided. */
    fun confirm() {
        val preview = mutableStep.value as? ImportStep.Preview ?: return
        if (!preview.ready || preview.working) return
        mutableStep.value = preview.copy(working = true, failed = false)
        viewModelScope.launch {
            val result = core.run { record { data, today -> data.applyImport(preview.plan, preview.rates, preview.replace, today) } }
            mutableStep.value = if (result is RecordingResult.Success) {
                val skipped = if (preview.replace) 0 else preview.plan.conflicts.size
                ImportStep.Done(preview.plan.rows.size - skipped, preview.plan.rows.map { it.account.lowercase() }.distinct().size)
            } else {
                preview.copy(working = false, failed = true)
            }
        }
    }

    /** Back to the start, forgetting any file read. */
    fun reset() {
        mutableStep.value = ImportStep.Start()
    }

    private fun updatePreview(change: (ImportStep.Preview) -> ImportStep.Preview) =
        mutableStep.update { if (it is ImportStep.Preview && !it.working) change(it) else it }

    companion object {
        /** Far more than years of values take. */
        const val MAX_IMPORT_BYTES: Int = 8 * 1024 * 1024
    }
}
