package net.kdt.pojavlaunch.lucifer.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.lucifer.LuciferViewModel
import net.kdt.pojavlaunch.lucifer.data.QuickAction
import net.kdt.pojavlaunch.lucifer.data.ServerInfo
import net.kdt.pojavlaunch.lucifer.theme.LucAccent
import net.kdt.pojavlaunch.lucifer.theme.LucBorder
import net.kdt.pojavlaunch.lucifer.theme.LucOnAccent
import net.kdt.pojavlaunch.lucifer.theme.LucSuccess
import net.kdt.pojavlaunch.lucifer.theme.LucTextMuted
import net.kdt.pojavlaunch.lucifer.theme.LucTextPrimary
import net.kdt.pojavlaunch.lucifer.ui.components.SectionHeader
import net.kdt.pojavlaunch.lucifer.ui.components.StatusDot
import net.kdt.pojavlaunch.lucifer.ui.components.glass

/**
 * RIGHT PANE — scrollable server list (simulated ping), 2x2 quick actions
 * and the READY footer indicator.
 */
@Composable
fun RightPane(vm: LuciferViewModel, modifier: Modifier = Modifier) {
    val servers by vm.servers.collectAsState()
    val status by vm.statusMessage.collectAsState()

    Column(
        modifier = modifier
            .glass()
            .padding(14.dp)
    ) {
        // Everything above the footer scrolls
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(text = "SERVERS", iconRes = R.drawable.ic_luc_info)

            // Scrollable server list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { server ->
                    ServerRow(server = server, onClick = {
                        // TODO: open server-detail / add-server sheet
                        Log.i("LuciferUI", "Server tapped: ${server.address} (TODO)")
                    })
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader(text = "QUICK ACTIONS", iconRes = R.drawable.ic_luc_bolt)

            // 2x2 quick action grid
            QuickAction.entries.chunked(2).forEach { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowActions.forEach { action ->
                        QuickActionTile(
                            action = action,
                            onClick = { vm.openQuickAction(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Keep grid square when a row has a single item
                    if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Footer status (pinned)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(if (status == "READY") LucSuccess else LucAccent)
            Spacer(Modifier.width(8.dp))
            Text(
                text = status,
                color = LucTextMuted,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** One server card: icon, name/address, player count, online dot. */
@Composable
fun ServerRow(server: ServerInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x660B1219))
            .border(1.dp, LucBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hex-ish server badge with initial
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    Brush.linearGradient(
                        listOf(serverAccent(server.name), Color(0xFF0B1219))
                    )
                )
                .border(1.dp, LucBorder, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = server.name.take(1).uppercase(),
                color = LucTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name.uppercase(),
                color = LucTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp
            )
            Text(text = server.address, color = LucTextMuted, fontSize = 10.sp)
        }
        Text(
            text = "${server.players}/${server.maxPlayers}",
            color = LucTextMuted,
            fontSize = 10.sp
        )
        Spacer(Modifier.width(8.dp))
        StatusDot(if (server.online) LucSuccess else Color(0xFFEF4444))
    }
}

/** Square quick-action tile: centered icon + label below. */
@Composable
fun QuickActionTile(action: QuickAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x660B1219))
            .border(1.dp, LucBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(action.iconRes),
            contentDescription = action.label,
            tint = LucAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(action.label, color = LucTextPrimary, fontSize = 11.sp)
    }
}

/** Deterministic per-server accent so badges differ visually. */
private fun serverAccent(name: String): Color = when (name.hashCode() and 3) {
    0 -> Color(0xFF00E5FF)
    1 -> Color(0xFF7C4DFF)
    2 -> Color(0xFF00E5A0)
    else -> Color(0xFFFFB300)
}
