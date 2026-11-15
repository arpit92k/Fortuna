package io.github.arpit92k.fortuna

import io.github.arpit92k.fortuna.about.AboutViewModel
import io.github.arpit92k.fortuna.dashboard.ZeroAccountsViewModel
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import io.github.arpit92k.fortuna.ui.PrivacyCover
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.dashboard.AmountsViewModel
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
import io.github.arpit92k.fortuna.lock.BiometricsViewModel
import io.github.arpit92k.fortuna.lock.LockViewModel
import io.github.arpit92k.fortuna.lock.RecoverViewModel
import io.github.arpit92k.fortuna.platform.AndroidDisplayPreferences
import io.github.arpit92k.fortuna.platform.AndroidDocumentFiles
import io.github.arpit92k.fortuna.platform.AppFocus
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

// A FragmentActivity, because Android's biometric prompt is shown as a fragment.
class MainActivity : FragmentActivity() {
    // Whether the app is the one in front. In the app switcher it stays resumed,
    // showing a live picture, but every window of the app loses focus.
    private var resumed by mutableStateOf(false)
    private var focusLost by mutableStateOf(false)
    private var splitScreen by mutableStateOf(false)

    /**
     * Covered while not in front, so the app switcher never shows figures. In split
     * screen, another app having focus is normal, so only leaving covers it there.
     */
    private val covered: Boolean get() = !resumed || (focusLost && !splitScreen)

    override fun onResume() {
        super.onResume()
        resumed = true
        splitScreen = isInMultiWindowMode
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        splitScreen = isInMultiWindowMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // In the released app, figures never appear in the app switcher or in
        // screenshots and screen recordings. Debug builds allow them, so the
        // screens can be captured while the app is developed. The recovery
        // phrase screen lifts this itself, so its words can be kept as a picture.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            // The app switcher keeps no picture of the app either.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
            // Its live picture there is covered. Only from Android 10, where the
            // app can tell its own dialogs taking focus from leaving the front.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AppFocus(window.decorView) { focusLost = it }
        }
        enableEdgeToEdge()
        val core = (application as FortunaApplication).core
        val files = AndroidDocumentFiles(this)
        val biometrics = (application as FortunaApplication).biometrics.also { it.attach(this) }
        val display = AndroidDisplayPreferences(this)
        setContent {
            // Scoped to the activity, so folding, unfolding and rotating keep what was typed.
            val zeroAccounts = viewModel { ZeroAccountsViewModel(display) }
            val screens = Screens(
                setup = viewModel { SetupViewModel(core, suggestedCurrency = regionCurrency()) },
                restore = viewModel { RestoreViewModel(core, files) },
                lock = viewModel { LockViewModel(core) },
                recover = viewModel { RecoverViewModel(core) },
                navigator = viewModel { Navigator() },
                dashboard = viewModel { DashboardViewModel(core, display, zeroAccounts.hidden) },
                addSource = viewModel { AddSourceViewModel(core) },
                sourcePage = viewModel { SourcePageViewModel(core) },
                valueEntry = viewModel { ValueEntryViewModel(core) },
                update = viewModel { UpdateViewModel(core, hideZero = zeroAccounts.hidden) },
                currencies = viewModel { CurrenciesViewModel(core) },
                backup = viewModel { BackupViewModel(core, files) },
                amounts = viewModel { AmountsViewModel(display) },
                zeroAccounts = zeroAccounts,
                about = viewModel { AboutViewModel(display) },
                biometrics = viewModel { BiometricsViewModel(core, biometrics) },
                report = viewModel { BugReportViewModel((application as FortunaApplication).crashes, deviceInfo()) },
            )
            Box {
                FortunaApp(core, screens)
                // Released builds only, like the screenshot block above.
                if (!BuildConfig.DEBUG) PrivacyCover(covered)
            }
        }
    }
}

/** The currency of the phone's region, if the app offers it. */
private fun regionCurrency(): Currency? =
    runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }
        .getOrNull()
        ?.let { Currency.forCode(it) }
