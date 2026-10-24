package io.github.arpit92k.fortuna.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.ui.AdaptiveScreen

/** Stands in for S-09 until step 16 builds the dashboard. */
@Composable
fun PlaceholderDashboard(baseCurrency: String?, onLock: () -> Unit) {
    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
                if (baseCurrency != null) {
                    Text(stringResource(R.string.home_base_currency, baseCurrency), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        action = {
            OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth().testTag("lock-now")) {
                Text(stringResource(R.string.home_lock_now))
            }
        },
    )
}
