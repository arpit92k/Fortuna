package io.github.arpit92k.fortuna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.ui.FortunaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = (application as FortunaApplication).session
        setContent {
            FortunaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The screens arrive from step 15. Until then, the state the core reports.
                        val state by session.state.collectAsState()
                        val text = when (state) {
                            SessionState.NoData -> R.string.state_no_data
                            is SessionState.SettingUp -> R.string.state_setting_up
                            is SessionState.Locked -> R.string.state_locked
                            SessionState.Unlocked -> R.string.state_unlocked
                        }
                        Text(text = stringResource(text))
                    }
                }
            }
        }
    }
}
