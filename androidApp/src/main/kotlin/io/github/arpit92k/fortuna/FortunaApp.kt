package io.github.arpit92k.fortuna

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.dashboard.DashboardScreen
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
import io.github.arpit92k.fortuna.lock.LockScreen
import io.github.arpit92k.fortuna.lock.LockViewModel
import io.github.arpit92k.fortuna.navigation.Destination
import io.github.arpit92k.fortuna.navigation.Navigator
import io.github.arpit92k.fortuna.navigation.entersValue
import io.github.arpit92k.fortuna.setup.SetupFlow
import io.github.arpit92k.fortuna.setup.SetupViewModel
import io.github.arpit92k.fortuna.source.AddSourceScreen
import io.github.arpit92k.fortuna.source.AddSourceViewModel
import io.github.arpit92k.fortuna.source.SourcePageScreen
import io.github.arpit92k.fortuna.source.SourcePageViewModel
import io.github.arpit92k.fortuna.source.ValueEntryScreen
import io.github.arpit92k.fortuna.source.valueEntrySourceOf
import io.github.arpit92k.fortuna.source.ValueEntryViewModel
import io.github.arpit92k.fortuna.ui.FortunaTheme
import io.github.arpit92k.fortuna.ui.WidthClass
import io.github.arpit92k.fortuna.update.UpdateStartScreen
import io.github.arpit92k.fortuna.update.UpdateStepScreen
import io.github.arpit92k.fortuna.update.UpdateViewModel
import kotlinx.datetime.toKotlinLocalDate

/** The app's view models, each made once for the activity. */
class Screens(
    val setup: SetupViewModel,
    val lock: LockViewModel,
    val navigator: Navigator,
    val dashboard: DashboardViewModel,
    val addSource: AddSourceViewModel,
    val sourcePage: SourcePageViewModel,
    val valueEntry: ValueEntryViewModel,
    val update: UpdateViewModel,
)

/**
 * The whole app: setup when there is no data, the lock screen while locked,
 * and the app itself once unlocked.
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
            when (state) {
                SessionState.NoData, is SessionState.SettingUp -> SetupFlow(screens.setup)
                is SessionState.Locked -> {
                    val ui by screens.lock.ui.collectAsState()
                    LockScreen(ui, onDigit = screens.lock::digit, onDelete = screens.lock::deleteDigit, onOk = screens.lock::ok)
                }
                SessionState.Unlocked -> UnlockedApp(core, screens)
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

    BackHandler(enabled = stack.size > 1) { leave(current, screens) }

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
            onLock = { core.post { lock() } },
            modifier = modifier,
        )
    }

    when (current) {
        Destination.Dashboard -> dashboard(Modifier)
        Destination.AddSource -> {
            val form by screens.addSource.form.collectAsState()
            val base = dataset?.baseCurrency ?: return
            AddSourceScreen(
                form = form,
                categories = dataset?.categories.orEmpty().filter { it.status == Status.ACTIVE },
                currency = base,
                onName = screens.addSource::name,
                onCategory = { screens.addSource.category(it.id) },
                onAmount = screens.addSource::amount,
                onDate = screens.addSource::date,
                onNote = screens.addSource::note,
                onSave = { screens.addSource.save(base) { navigator.back() } },
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
                    onBack = { leave(current, screens) },
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
                onDate = screens.update::date,
                onStart = {
                    screens.update.start()
                    navigator.open(Destination.UpdateStep)
                },
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
            )
        }
    }
}

/** Leaves [current], forgetting anything typed on a form left without saving. */
private fun leave(current: Destination, screens: Screens) {
    when (current) {
        Destination.AddSource -> screens.addSource.discard()
        is Destination.ValueEntry -> screens.valueEntry.discard()
        // Leaving the walk keeps the date; what was entered is already saved.
        Destination.UpdateStep -> screens.update.stop()
        Destination.Update -> screens.update.finish()
        else -> {}
    }
    screens.navigator.back()
}
