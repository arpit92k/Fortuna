package io.github.arpit92k.fortuna

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.dashboard.AmountsViewModel
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
import io.github.arpit92k.fortuna.lock.LockViewModel
import io.github.arpit92k.fortuna.lock.RecoverViewModel
import io.github.arpit92k.fortuna.platform.AndroidDisplayPreferences
import io.github.arpit92k.fortuna.platform.AndroidDocumentFiles
import io.github.arpit92k.fortuna.platform.deviceInfo
import io.github.arpit92k.fortuna.report.BugReportViewModel
import io.github.arpit92k.fortuna.navigation.Navigator
import io.github.arpit92k.fortuna.settings.BackupViewModel
import io.github.arpit92k.fortuna.settings.CurrenciesViewModel
import io.github.arpit92k.fortuna.setup.RestoreViewModel
import io.github.arpit92k.fortuna.setup.SetupViewModel
import io.github.arpit92k.fortuna.source.AddSourceViewModel
import io.github.arpit92k.fortuna.source.SourcePageViewModel
import io.github.arpit92k.fortuna.source.ValueEntryViewModel
import io.github.arpit92k.fortuna.update.UpdateViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // In the released app, figures never appear in the app switcher or in
        // screenshots and screen recordings. Debug builds allow them, so the
        // screens can be captured while the app is developed. The recovery
        // phrase screen lifts this itself, so its words can be kept as a picture.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val core = (application as FortunaApplication).core
        val files = AndroidDocumentFiles(this)
        val display = AndroidDisplayPreferences(this)
        setContent {
            // Scoped to the activity, so folding, unfolding and rotating keep what was typed.
            val screens = Screens(
                setup = viewModel { SetupViewModel(core, suggestedCurrency = regionCurrency()) },
                restore = viewModel { RestoreViewModel(core, files) },
                lock = viewModel { LockViewModel(core) },
                recover = viewModel { RecoverViewModel(core) },
                navigator = viewModel { Navigator() },
                dashboard = viewModel { DashboardViewModel(core) },
                addSource = viewModel { AddSourceViewModel(core) },
                sourcePage = viewModel { SourcePageViewModel(core) },
                valueEntry = viewModel { ValueEntryViewModel(core) },
                update = viewModel { UpdateViewModel(core) },
                currencies = viewModel { CurrenciesViewModel(core) },
                backup = viewModel { BackupViewModel(core, files) },
                amounts = viewModel { AmountsViewModel(display) },
                report = viewModel { BugReportViewModel((application as FortunaApplication).crashes, deviceInfo()) },
            )
            FortunaApp(core, screens)
        }
    }
}

/** The currency of the phone's region, if the app offers it. */
private fun regionCurrency(): Currency? =
    runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }
        .getOrNull()
        ?.let { Currency.forCode(it) }
