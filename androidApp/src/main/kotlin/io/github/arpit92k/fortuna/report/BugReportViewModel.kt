package io.github.arpit92k.fortuna.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S-18, and the offer after a crash. */
data class BugReportUi(
    /** Whether S-18 is open. It opens over whatever is showing, locked or not, since a report needs no PIN. */
    val open: Boolean = false,
    val description: String = "",
    /** Saved crash records, included in the report. */
    val crashes: List<String> = emptyList(),
    /** Whether to offer, on start, to report a crash saved last time. */
    val offerCrash: Boolean = false,
    /** Sending found no email app on the phone. */
    val noEmailApp: Boolean = false,
)

/**
 * Bug reports (UF-18). The report leaves through the user's email app, which
 * opens addressed and filled in, so nothing is sent until the user sends it
 * there; Fortuna itself has no network access.
 */
class BugReportViewModel(
    private val crashes: CrashStore,
    private val device: DeviceInfo,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableUi = MutableStateFlow(BugReportUi())
    val ui: StateFlow<BugReportUi> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(io) { crashes.records() }
            mutableUi.update { it.copy(crashes = saved, offerCrash = saved.isNotEmpty()) }
        }
    }

    /** The report as it will be sent. */
    val reportText: String get() = reportText(mutableUi.value.description, device, mutableUi.value.crashes)

    fun open() {
        mutableUi.update { it.copy(open = true, offerCrash = false, noEmailApp = false) }
    }

    fun close() {
        mutableUi.update { it.copy(open = false, description = "", noEmailApp = false) }
    }

    fun description(text: String) {
        mutableUi.update { it.copy(description = text, noEmailApp = false) }
    }

    /** "Not now" on the offer: asked again next time the app opens. */
    fun notNow() {
        mutableUi.update { it.copy(offerCrash = false) }
    }

    /** Deletes the saved crashes instead of reporting them. */
    fun deleteCrashes() {
        mutableUi.update { it.copy(crashes = emptyList(), offerCrash = false) }
        viewModelScope.launch { withContext(io) { crashes.clear() } }
    }

    /**
     * Hands the report to the email app through [openEmail], which returns
     * false if there is none. Once handed over, the crashes it holds are
     * deleted, so they are not offered again.
     */
    fun send(openEmail: (EmailDraft) -> Boolean) {
        val ui = mutableUi.value
        if (!openEmail(emailDraftOf(ui.description, device, ui.crashes))) {
            mutableUi.update { it.copy(noEmailApp = true) }
            return
        }
        if (ui.crashes.isNotEmpty()) deleteCrashes()
        close()
    }
}
