package io.github.arpit92k.fortuna

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.home.PlaceholderDashboard
import io.github.arpit92k.fortuna.lock.LockScreen
import io.github.arpit92k.fortuna.lock.LockViewModel
import io.github.arpit92k.fortuna.setup.SetupFlow
import io.github.arpit92k.fortuna.setup.SetupViewModel
import io.github.arpit92k.fortuna.ui.FortunaTheme

/**
 * The whole app: setup when there is no data, the lock screen while locked,
 * and the app itself once unlocked.
 *
 * The lock screen goes over whatever was open. The unlocked screens keep
 * their place and anything typed in view models that outlive the lock, so
 * unlocking returns the user to where they were (UF-02).
 */
@Composable
fun FortunaApp(core: Core, setup: SetupViewModel, lock: LockViewModel) {
    val state by core.state.collectAsState()
    FortunaTheme {
        Surface(Modifier.fillMaxSize()) {
            when (state) {
                SessionState.NoData, is SessionState.SettingUp -> SetupFlow(setup)
                is SessionState.Locked -> {
                    val ui by lock.ui.collectAsState()
                    LockScreen(ui, onDigit = lock::digit, onDelete = lock::deleteDigit, onOk = lock::ok)
                }
                SessionState.Unlocked -> {
                    val dataset by core.dataset.collectAsState()
                    PlaceholderDashboard(
                        baseCurrency = dataset?.baseCurrency?.let { "${it.code} (${it.name})" },
                        onLock = { core.post { lock() } },
                    )
                }
            }
        }
    }
}
