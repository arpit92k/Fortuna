package io.github.arpit92k.fortuna

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.lock.LockViewModel
import io.github.arpit92k.fortuna.setup.SetupViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Figures and the recovery phrase never appear in the app switcher or
        // in screenshots and screen recordings.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val core = (application as FortunaApplication).core
        setContent {
            // Scoped to the activity, so folding, unfolding and rotating keep what was typed.
            val setup = viewModel { SetupViewModel(core, suggestedCurrency = regionCurrency()) }
            val lock = viewModel { LockViewModel(core) }
            FortunaApp(core, setup, lock)
        }
    }
}

/** The currency of the phone's region, if the app offers it. */
private fun regionCurrency(): Currency? =
    runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }
        .getOrNull()
        ?.let { Currency.forCode(it) }
