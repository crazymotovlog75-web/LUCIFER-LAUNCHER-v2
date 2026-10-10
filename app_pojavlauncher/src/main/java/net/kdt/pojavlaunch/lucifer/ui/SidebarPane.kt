package net.kdt.pojavlaunch.lucifer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.lucifer.data.NavDestination
import net.kdt.pojavlaunch.lucifer.theme.LucAccent
import net.kdt.pojavlaunch.lucifer.theme.LucBorder
import net.kdt.pojavlaunch.lucifer.theme.LucOnAccent
import net.kdt.pojavlaunch.lucifer.theme.LucSuccess
import net.kdt.pojavlaunch.lucifer.theme.LucTextMuted
import net.kdt.pojavlaunch.lucifer.theme.LucTextPrimary
import net.kdt.pojavlaunch.lucifer.ui.components.StatusDot
import net.kdt.pojavlaunch.lucifer.ui.components.glass

/**
 * LEFT PANE — logo, glowing profile badge and the vertical nav menu.
 * [compact] collapses labels (icon rail) on narrow / portrait windows.
 */
@Composable
fun SidebarPane(
    currentRoute: String,
    onNavigate: (NavDestination) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glass()
            .padding(if (compact) 10.dp else 16.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
    ) {
        // ── Logo ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 18.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = "LUCIFER",
                color = LucTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 5.sp,
                fontSize = if (compact) 14.sp else 22.sp
            )
            if (!compact) {
                Text(
                    text = "LAUNCHER",
                    color = LucAccent,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 8.sp,
                    fontSize = 9.sp
                )
            }
        }

        // ── Profile ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // Neon-ringed avatar (Coil, bundled fallback painter)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, LucAccent, CircleShape)
                    .border(5.dp, Color(0x3300E5FF), CircleShape)
            ) {
                AsyncImage(
                    model = "https://mc-heads.net/avatar/MHF_Steve/128",
                    contentDescription = "Player avatar",
                    placeholder = painterResource(R.drawable.avatar_apex),
                    error = painterResource(R.drawable.avatar_apex),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            if (!compact) {
                Box(Modifier.size(10.dp))
                Text(
                    text = "APEX_GAMER",
                    color = LucTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Box(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(LucSuccess)
                    Box(Modifier.size(6.dp))
                    Text(
                        text = "ONLINE",
                        color = LucTextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }

        // ── Nav menu ────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NavDestination.entries.forEach { dest ->
                NavRow(
                    destination = dest,
                    active = dest.route == currentRoute,
                    compact = compact,
                    onClick = { onNavigate(dest) }
                )
            }
        }
    }
}

@Composable
private fun NavRow(
    destination: NavDestination,
    active: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) LucAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = destination.label,
            tint = if (active) LucOnAccent else LucTextMuted,
            modifier = Modifier.size(18.dp)
        )
        if (!compact) {
            Box(Modifier.size(12.dp))
            Text(
                text = destination.label,
                color = if (active) LucOnAccent else LucTextMuted,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}
