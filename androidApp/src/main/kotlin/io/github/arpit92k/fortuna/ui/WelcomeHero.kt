package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// Decided: the welcome picture and its three rows say the same three things,
// in the same order. The icons that circle the mark are the icons of the rows,
// clockwise from the top, so the picture reads at a glance and the rows explain it.

/**
 * The mark inside its rings, with the three promises' icons on the outer
 * ring: on this phone at the top, no account at the lower right, the PIN and
 * phrase at the lower left. Decorative: the rows beneath say it in words.
 */
@Composable
fun WelcomeHero(modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = HeroWidth)
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(WELCOME_HERO),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            RingedMark(radius = ORBIT)
            OrbitBadge(FortunaIcon.Phone, Modifier.offset(x = 0.dp, y = -ORBIT))
            OrbitBadge(FortunaIcon.NoCloud, Modifier.offset(x = ORBIT * 0.866f, y = ORBIT * 0.5f))
            OrbitBadge(FortunaIcon.Lock, Modifier.offset(x = -ORBIT * 0.866f, y = ORBIT * 0.5f))
        }
    }
}

/** The radius of the ring the icons sit on. */
private val ORBIT = 60.dp

@Composable
private fun OrbitBadge(icon: FortunaIcon, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FortunaIconImage(icon, size = 20.dp, line = accent, accent = accent)
    }
}

/** One promise: [icon] on a tinted circle, a short [title], and a line saying more. */
@Composable
fun PromiseRow(icon: FortunaIcon, title: String, body: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            FortunaIconImage(icon, size = 24.dp, line = accent, accent = accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

const val WELCOME_HERO = "welcome-hero"
