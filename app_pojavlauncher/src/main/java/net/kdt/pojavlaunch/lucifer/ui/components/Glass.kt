package net.kdt.pojavlaunch.lucifer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.lucifer.theme.LucAccent
import net.kdt.pojavlaunch.lucifer.theme.LucBorder
import net.kdt.pojavlaunch.lucifer.theme.LucOnAccent
import net.kdt.pojavlaunch.lucifer.theme.LucPanel
import net.kdt.pojavlaunch.lucifer.theme.LucTextMuted
import net.kdt.pojavlaunch.lucifer.theme.LucTextPrimary

/**
 * Frosted-glass panel: translucent fill + 1dp #1F2937 border, 16dp corners.
 * Active variants pass [active] = true for a neon cyan border instead.
 */
@Composable
fun Modifier.glass(active: Boolean = false): Modifier = this
    .background(LucPanel, RoundedCornerShape(16.dp))
    .border(
        width = 1.dp,
        color = if (active) LucAccent else LucBorder,
        shape = RoundedCornerShape(16.dp)
    )

/** Generic glass container with column content. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.glass(active).padding(14.dp), content = content)
}

/** Small muted uppercase section header with optional leading icon. */
@Composable
fun SectionHeader(
    text: String,
    @DrawableRes iconRes: Int? = null,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = LucAccent,
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 0.dp)
            )
            Box(Modifier.size(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = LucTextMuted
        )
    }
}

/** Tiny circular status LED (online / ready / launching). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** Rounded metadata pill, e.g. "1.21.4" / "Fabric". */
@Composable
fun MetaPill(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x660B1219))
            .border(1.dp, LucBorder, RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = LucAccent,
            modifier = Modifier.size(14.dp)
        )
        Box(Modifier.size(6.dp))
        Text(text, color = LucTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Dark secondary action button with cyan icon (Edit Profile / Folder…). */
@Composable
fun RowScope.SecondaryActionButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x660B1219))
            .border(1.dp, LucBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = LucAccent,
            modifier = Modifier.size(16.dp)
        )
        Box(Modifier.size(8.dp))
        Text(label, color = LucTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
