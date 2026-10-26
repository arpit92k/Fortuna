package io.github.arpit92k.fortuna

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.arpit92k.fortuna.source.ConfirmDialog
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.dashboard.AmountsViewModel
import io.github.arpit92k.fortuna.dashboard.DashboardScreen
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
import io.github.arpit92k.fortuna.lock.LockScreen
import io.github.arpit92k.fortuna.platform.openEmail
import io.github.arpit92k.fortuna.report.BugReportScreen
import io.github.arpit92k.fortuna.report.BugReportViewModel
import io.github.arpit92k.fortuna.report.CrashOfferDialog
import io.github.arpit92k.fortuna.lock.LockViewModel
import io.github.arpit92k.fortuna.lock.RecoverFlow
import io.github.arpit92k.fortuna.lock.RecoverViewModel
import io.github.arpit92k.fortuna.navigation.Destination
import io.github.arpit92k.fortuna.navigation.Navigator
import io.github.arpit92k.fortuna.navigation.entersValue
import io.github.arpit92k.fortuna.settings.BackupScreen
import io.github.arpit92k.fortuna.settings.BackupViewModel
import io.github.arpit92k.fortuna.settings.CurrenciesScreen
import io.github.arpit92k.fortuna.settings.CurrenciesViewModel
import io.github.arpit92k.fortuna.settings.SettingsScreen
import io.github.arpit92k.fortuna.setup.RestoreViewModel
import io.github.arpit92k.fortuna.setup.SetupFlow
import io.github.arpit92k.fortuna.setup.SetupViewModel
import io.github.arpit92k.fortuna.source.AddSourceScreen
import io.github.arpit92k.fortuna.source.AddSourceViewModel
import io.github.arpit92k.fortuna.source.SourcePageScreen
import io.github.arpit92k.fortuna.source.SourcePageViewModel
import io.github.arpit92k.fortuna.source.ValueEntryScreen
import io.github.arpit92k.fortuna.source.valueEntrySourceOf
import io.github.arpit92k.fortuna.source.ValueEntryViewModel
import io.github.arpit92k.fortuna.source.rateInUse
import io.github.arpit92k.fortuna.ui.FortunaTheme
import io.github.arpit92k.fortuna.ui.LocalAmountsHidden
import io.github.arpit92k.fortuna.ui.WidthClass
import io.github.arpit92k.fortuna.update.UpdateStartScreen
import io.github.arpit92k.fortuna.update.UpdateStepScreen
import io.github.arpit92k.fortuna.update.UpdateViewModel
import kotlinx.datetime.toKotlinLocalDate

/** The app's view models, each made once for the activity. */
class Screens(
    val setup: SetupViewModel,
    val restore: RestoreViewModel,
    val lock: LockViewModel,
    val recover: RecoverViewModel,
    val navigator: Navigator,
    val dashboard: DashboardViewModel,
    val addSource: AddSourceViewModel,
    val sourcePage: SourcePageViewModel,
    val valueEntry: ValueEntryViewModel,
    val update: UpdateViewModel,
    val currencies: CurrenciesViewModel,
    val backup: BackupViewModel,
    val amounts: AmountsViewModel,
    val report: BugReportViewModel,
)

/**
 * The whole app: setup or a restore when there is no data, the lock screen or
 * the way in with the recovery phrase while locked, and the app itself once
 * unlocked.
 *
 * The lock screen goes over whatever was open. Where the user was, and anything
 * typed, live in view models that outlive the lock, so unlocking returns the
 * user to where they were (UF-02).
 */
@Composable
fun FortunaApp(core: Core, screens: Screens) {
    val state by core.state.collectAsState()
    FortunaTheme {
        Surface(Modifier.fillMaxSize()) {
            val report by screens.report.ui.collectAsState()
            // S-18 goes over whatever is showing, locked or not: a report needs no PIN.
            if (report.open) {
                val context = LocalContext.current
                BugReportScreen(
                    ui = report,
                    report = screens.report.reportText,
                    onDescription = screens.report::description,
                    onSend = { screens.report.send { openEmail(context, it) } },
                    onDeleteCrashes = screens.report::deleteCrashes,
                    onBack = screens.report::close,
                )
                return@Surface
            }
            if (report.offerCrash) {
                CrashOfferDialog(onReport = screens.report::open, onDelete = screens.report::deleteCrashes, onNotNow = screens.report::notNow)
            }
            when (state) {
                SessionState.NoData, is SessionState.SettingUp -> SetupFlow(screens.setup, screens.restore, onReport = screens.report::open)
                is SessionState.Locked -> {
                    val recovering by screens.recover.step.collectAsState()
                    if (recovering != null) {
                        RecoverFlow(screens.recover)
                    } else {
                        val ui by screens.lock.ui.collectAsState()
                        LockScreen(
                            ui,
                            onDigit = screens.lock::digit,
                            onDelete = screens.lock::deleteDigit,
                            onOk = screens.lock::ok,
                            onForgotPin = screens.recover::begin,
                            onReport = screens.report::open,
                        )
                    }
                }
                SessionState.Unlocked -> {
                    // Hidden or shown everywhere at once, as the dashboard's eye says.
                    val hidden by screens.amounts.hidden.collectAsState()
                    CompositionLocalProvider(LocalAmountsHidden provides hidden) { UnlockedApp(core, screens) }
                }
            }
        }
    }
}

@Composable
private fun UnlockedApp(core: Core, screens: Screens) {
    val stack by screens.navigator.stack.collectAsState()
    val dataset by core.dataset.collectAsState()
    val current = stack.last()
    val navigator = screens.navigator

    // A screen where a value is entered earns the two-minute grace period.
    LaunchedEffect(current) {
        val entering = entersValue(current)
        core.post { enteringValue = entering }
    }

    // A source deleted, here or elsewhere, takes its screens with it.
    LaunchedEffect(current, dataset) {
        val id = (current as? Destination.Source)?.id ?: (current as? Destination.ValueEntry)?.id
        val data = dataset
        if (id != null && data != null && data.source(id) == null) navigator.forget(id)
    }

    // Leaving a page with something typed and not saved asks first, from the arrow and the system back alike.
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    val tryLeave: () -> Unit = {
        if (hasUnsavedInput(current, screens)) {
            confirmingLeave = true
        } else {
            leave(current, screens)
        }
    }
    if (confirmingLeave) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_text),
            confirm = stringResource(R.string.discard_confirm),
            onConfirm = {
                confirmingLeave = false
                leave(current, screens)
            },
            onDismiss = { confirmingLeave = false },
            dismiss = stringResource(R.string.discard_keep),
        )
    }

    BackHandler(enabled = stack.size > 1) { tryLeave() }

    val dashboard: @Composable (Modifier) -> Unit = { modifier ->
        val ui by screens.dashboard.ui.collectAsState()
        DashboardScreen(
            ui = ui,
            onAddSource = { navigator.open(Destination.AddSource) },
            onOpen = { navigator.open(Destination.Source(it)) },
            onUpdate = {
                screens.update.prepare()
                navigator.open(Destination.Update)
            },
            onSettings = { navigator.open(Destination.Settings) },
            onLock = { core.post { lock() } },
            onToggleAmounts = screens.amounts::toggle,
            modifier = modifier,
        )
    }

    when (current) {
        Destination.Dashboard -> dashboard(Modifier)
        Destination.AddSource -> {
            val form by screens.addSource.form.collectAsState()
            val data = dataset ?: return
            val base = data.baseCurrency ?: return
            val today = java.time.LocalDate.now().toKotlinLocalDate()
            AddSourceScreen(
                form = form,
                categories = data.categories.filter { it.status == Status.ACTIVE },
                base = base,
                rateInUse = rateInUse(data, form.currency ?: base, form.date ?: today),
                today = today,
                onName = screens.addSource::name,
                onCategory = { screens.addSource.category(it.id) },
                onCurrency = screens.addSource::currency,
                onAmount = screens.addSource::amount,
                onDate = screens.addSource::date,
                onRate = screens.addSource::rate,
                onNote = screens.addSource::note,
                onSave = { screens.addSource.save(base) { navigator.back() } },
                onBack = tryLeave,
            )
        }
        is Destination.Source -> BoxWithConstraints {
            LaunchedEffect(current.id) { screens.sourcePage.show(current.id) }
            val ui by screens.sourcePage.ui.collectAsState()
            val page: @Composable (Modifier) -> Unit = { modifier ->
                SourcePageScreen(
                    ui = ui?.takeIf { it.id == current.id },
                    onAddValue = { navigator.open(Destination.ValueEntry(current.id)) },
                    onEdit = { navigator.open(Destination.ValueEntry(current.id, editing = it)) },
                    onDeleteLatest = { screens.sourcePage.deleteLatest(current.id) },
                    onDeleteSource = { screens.sourcePage.deleteSource(current.id) { navigator.forget(current.id) } },
                    onBack = tryLeave,
                    modifier = modifier,
                )
            }
            // On an expanded window the list stays beside the source it opened.
            if (WidthClass.of(maxWidth) == WidthClass.EXPANDED) {
                Row(Modifier.fillMaxSize()) {
                    dashboard(Modifier.weight(0.42f))
                    VerticalDivider()
                    page(Modifier.weight(0.58f))
                }
            } else {
                page(Modifier)
            }
        }
        Destination.Update -> {
            val ui by screens.update.ui.collectAsState()
            UpdateStartScreen(
                ui = ui,
                base = dataset?.baseCurrency,
                onDate = screens.update::date,
                onRate = screens.update::recordRate,
                onStart = {
                    screens.update.start()
                    navigator.open(Destination.UpdateStep)
                },
                onBack = tryLeave,
            )
        }
        Destination.UpdateStep -> {
            val ui by screens.update.ui.collectAsState()
            // After the last source, back to the dashboard.
            LaunchedEffect(ui.started, ui.current == null, ui.plan != null) {
                if (ui.started && ui.plan != null && ui.current == null) {
                    screens.update.finish()
                    navigator.reset()
                }
            }
            UpdateStepScreen(
                ui = ui,
                onValue = screens.update::value,
                onAdded = screens.update::added,
                onNote = screens.update::note,
                onSave = screens.update::save,
                onSkip = screens.update::skip,
                onBack = tryLeave,
            )
        }
        Destination.Settings -> SettingsScreen(
            onCurrencies = { navigator.open(Destination.Currencies) },
            onBackup = { navigator.open(Destination.Backup) },
            onReport = screens.report::open,
            onBack = tryLeave,
        )
        Destination.Backup -> {
            val ui by screens.backup.ui.collectAsState()
            // The system picker: the user chooses where the file goes, and the app may write only there.
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_TYPE)) { uri ->
                screens.backup.save(uri?.toString())
            }
            BackupScreen(
                ui = ui,
                onSave = {
                    screens.backup.clearMessage()
                    pick.launch(screens.backup.suggestedName(java.time.LocalDate.now().toKotlinLocalDate()))
                },
                onBack = tryLeave,
            )
        }
        Destination.Currencies -> {
            val ui by screens.currencies.ui.collectAsState()
            CurrenciesScreen(
                ui = ui,
                onSave = screens.currencies::saveRate,
                onDelete = screens.currencies::deleteRate,
                onClearProblem = screens.currencies::clearProblem,
                onBack = tryLeave,
            )
        }
        is Destination.ValueEntry -> {
            val data = dataset ?: return
            val source = valueEntrySourceOf(data, current.id, java.time.LocalDate.now().toKotlinLocalDate()) ?: return
            val snapshot = current.editing?.let { data.source(current.id)?.snapshots?.on(it) }
            LaunchedEffect(current) {
                screens.valueEntry.open(source, current.editing, snapshot?.let { it.value to it.amountAdded }, snapshot?.note)
            }
            val form by screens.valueEntry.form.collectAsState()
            ValueEntryScreen(
                form = form,
                source = source,
                name = data.source(current.id)?.name.orEmpty(),
                editing = current.editing,
                onValue = screens.valueEntry::value,
                onAdded = screens.valueEntry::added,
                onDate = screens.valueEntry::date,
                onNote = screens.valueEntry::note,
                onSave = { screens.valueEntry.save(source, current.editing) { navigator.back() } },
                onReplace = { screens.valueEntry.save(source, current.editing, replace = true) { navigator.back() } },
                onKeep = screens.valueEntry::keepExisting,
                onBack = tryLeave,
            )
        }
    }
}

/** A backup is not a type the phone knows, so it is saved as plain bytes. */
private const val BACKUP_TYPE = "application/octet-stream"

/** Whether [current] has something typed and not saved, which leaving would lose. */
private fun hasUnsavedInput(current: Destination, screens: Screens): Boolean = when (current) {
    Destination.AddSource -> screens.addSource.hasInput
    is Destination.ValueEntry -> screens.valueEntry.hasChanges
    Destination.UpdateStep -> screens.update.hasInput
    else -> false
}

/** Leaves [current], forgetting anything typed on a form left without saving. */
private fun leave(current: Destination, screens: Screens) {
    when (current) {
        Destination.AddSource -> screens.addSource.discard()
        is Destination.ValueEntry -> screens.valueEntry.discard()
        // Leaving the walk keeps the date; what was entered is already saved.
        Destination.UpdateStep -> screens.update.stop()
        Destination.Update -> screens.update.finish()
        Destination.Currencies -> screens.currencies.clearProblem()
        Destination.Backup -> screens.backup.clearMessage()
        else -> {}
    }
    screens.navigator.back()
}
