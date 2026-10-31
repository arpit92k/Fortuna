package io.github.arpit92k.fortuna.about

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.BuildConfig
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.ui.BackButton
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.ReadableWidth
import kotlinx.coroutines.launch

/** One card of the tour: what the app does, in a picture, a title and a few lines. */
private data class AboutPage(val icon: FortunaIcon, @StringRes val title: Int, @StringRes val body: Int)

private val pages = listOf(
    AboutPage(FortunaIcon.NoCloud, R.string.about_private_title, R.string.about_private_body),
    AboutPage(FortunaIcon.Investments, R.string.about_accounts_title, R.string.about_accounts_body),
    AboutPage(FortunaIcon.Update, R.string.about_update_title, R.string.about_update_body),
    AboutPage(FortunaIcon.Expand, R.string.about_insights_title, R.string.about_insights_body),
    AboutPage(FortunaIcon.Backup, R.string.about_backup_title, R.string.about_backup_body),
)

/**
 * About Fortuna: a few cards on what the app does, swiped through. Shown once
 * after the app is first opened, with Skip and Done, and from Settings, with
 * a back arrow and the app's version.
 */
@Composable
fun AboutScreen(firstRun: Boolean, onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("about"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ReadableWidth).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!firstRun) BackButton(onDone)
                Spacer(Modifier.weight(1f))
                if (firstRun && !last) {
                    TextButton(onClick = onDone, modifier = Modifier.testTag("about-skip")) { Text(stringResource(R.string.about_skip)) }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                val page = pages[index]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp).testTag("about-page-$index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    IconTile(page.icon, size = 88.dp)
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    if (!firstRun && index == pages.lastIndex) {
                        Text(
                            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("about-version"),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Where the user is among the cards; the pager itself tells screen readers.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in pages.indices) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
                if (last) {
                    Button(onClick = onDone, modifier = Modifier.testTag("about-done")) { Text(stringResource(R.string.about_done)) }
                } else {
                    TextButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.testTag("about-next"),
                    ) { Text(stringResource(R.string.about_next)) }
                }
            }
        }
    }
}
