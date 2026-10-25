package io.github.arpit92k.fortuna

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import io.github.arpit92k.fortuna.ui.FortunaTheme

/** The app's view models, each made once for the activity. */
class Screens(
    val setup: SetupViewModel,
    val lock: LockViewModel,
    val navigator: Navigator,
    val dashboard: DashboardViewModel,
    val addSource: AddSourceViewModel,
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
    val current = stack.last()

    // A screen where a value is entered earns the two-minute grace period.
    LaunchedEffect(current) {
        val entering = entersValue(current)
        core.post { enteringValue = entering }
    }

    BackHandler(enabled = stack.size > 1) {
        if (current == Destination.AddSource) screens.addSource.discard()
        screens.navigator.back()
    }

    when (current) {
        Destination.Dashboard -> {
            val ui by screens.dashboard.ui.collectAsState()
            DashboardScreen(
                ui = ui,
                onAddSource = { screens.navigator.open(Destination.AddSource) },
                onLock = { core.post { lock() } },
            )
        }
        Destination.AddSource -> {
            val dataset by core.dataset.collectAsState()
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
                onSave = { screens.addSource.save(base) { screens.navigator.back() } },
            )
        }
    }
}
