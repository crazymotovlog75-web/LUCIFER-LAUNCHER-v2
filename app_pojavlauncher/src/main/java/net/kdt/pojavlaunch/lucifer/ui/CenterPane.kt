package net.kdt.pojavlaunch.lucifer.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.lucifer.LuciferViewModel
import net.kdt.pojavlaunch.lucifer.backend.LaunchBackend
import net.kdt.pojavlaunch.lucifer.data.InstanceInfo
import net.kdt.pojavlaunch.lucifer.theme.LucAccent
import net.kdt.pojavlaunch.lucifer.theme.LucBorder
import net.kdt.pojavlaunch.lucifer.theme.LucOnAccent
import net.kdt.pojavlaunch.lucifer.theme.LucTextMuted
import net.kdt.pojavlaunch.lucifer.theme.LucTextPrimary
import net.kdt.pojavlaunch.lucifer.ui.components.MetaPill
import net.kdt.pojavlaunch.lucifer.ui.components.SecondaryActionButton
import net.kdt.pojavlaunch.lucifer.ui.components.SectionHeader
import net.kdt.pojavlaunch.lucifer.ui.components.glass

/* ═══════════════════════ HOME (CURRENT INSTANCE) ═══════════════════════ */

@Composable
fun HomeScreen(vm: LuciferViewModel, embeddedRightPane: @Composable () -> Unit) {
    val currentInstance by vm.currentInstance.collectAsState()
    val progress by vm.launchProgress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .glass()
                .padding(16.dp)
        ) {
            SectionHeader(
                text = "CURRENT INSTANCE",
                iconRes = R.drawable.ic_luc_cube,
                centered = true
            )
            Spacer(Modifier.height(10.dp))

            // ── Hero card ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 260.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LucBorder, RoundedCornerShape(16.dp))
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.instance_hero),
                    contentDescription = "Instance artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // Bottom scrim for legibility
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC0A0F14))
                            )
                        )
                )
            }
            Spacer(Modifier.height(14.dp))

            // ── Instance title + metadata pills ────────────────────────────
            Text(
                text = currentInstance,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaPill(iconRes = R.drawable.ic_luc_tag, text = "1.21.4")
                MetaPill(iconRes = R.drawable.ic_luc_chip, text = "Fabric")
            }
            Spacer(Modifier.height(18.dp))

            // ── PLAY ────────────────────────────────────────────────────────
            Button(
                onClick = { (LocalContext.current as? Activity)?.let { vm.play(it) } },
                enabled = progress == null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LucAccent,
                    contentColor = LucOnAccent,
                    disabledContainerColor = LucAccent.copy(alpha = 0.75f),
                    disabledContentColor = LucOnAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (progress == null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_luc_play),
                        contentDescription = null,
                        tint = LucOnAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "PLAY",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LucOnAccent,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(progress!!.step, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress!!.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = LucAccent,
                    trackColor = Color(0x1A00E5FF)
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Secondary actions ───────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    iconRes = R.drawable.ic_luc_edit,
                    label = "Edit Profile",
                    onClick = { LaunchBackend.editProfile(currentInstance) }
                )
                SecondaryActionButton(
                    iconRes = R.drawable.ic_luc_folder,
                    label = "Folder",
                    onClick = { LaunchBackend.openGameDirectory(LocalContext.current) }
                )
            }
        }

        // On medium/narrow windows the right pane merges under the hero card
        Spacer(Modifier.height(12.dp))
        embeddedRightPane()
    }
}

/* ═══════════════════════ INSTANCES ═══════════════════════ */

@Composable
fun InstancesScreen(vm: LuciferViewModel) {
    val instances by vm.instances.collectAsState()
    val current by vm.currentInstance.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.glass().padding(16.dp)) {
            SectionHeader(text = "INSTALLED INSTANCES", iconRes = R.drawable.ic_luc_cube)
            Spacer(Modifier.height(8.dp))
            instances.forEach { instance ->
                InstanceCard(
                    instance = instance,
                    active = instance.name.equals(current, ignoreCase = true),
                    onClick = { vm.selectInstance(context, instance.name) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { /* TODO: create-instance wizard */ Log.i("LuciferUI", "TODO: new instance") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A00E5FF), contentColor = LucAccent),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("+  New Instance", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun InstanceCard(instance: InstanceInfo, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x660B1219))
            .border(1.dp, if (active) LucAccent else LucBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(instance.name, color = LucTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${instance.version}  ·  ${instance.loader}", color = LucTextMuted, fontSize = 11.sp)
        }
        if (active) {
            Text("ACTIVE", color = LucAccent, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ═══════════════════════ MOD STORE ═══════════════════════ */

@Composable
fun ModStoreScreen(vm: LuciferViewModel) {
    var query by remember { mutableStateOf("") }
    val mods = listOf("Sodium" to "Performance", "Iris" to "Shaders", "Fabric API" to "Library", "Jade" to "HUD")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.glass().padding(16.dp)) {
            SectionHeader(text = "MOD STORE", iconRes = R.drawable.ic_luc_store)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search mods…", color = LucTextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LucAccent,
                    unfocusedBorderColor = LucBorder,
                    focusedTextColor = LucTextPrimary,
                    unfocusedTextColor = LucTextPrimary,
                    cursorColor = LucAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            mods.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (name, category) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x660B1219))
                                .border(1.dp, LucBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(name, color = LucTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(category, color = LucTextMuted, fontSize = 10.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "INSTALL",
                                color = LucAccent,
                                fontSize = 10.sp,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { vm.showNotice(R.string.lucifer_stub_notice) }
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/* ═══════════════════════ CONTROLS ═══════════════════════ */

@Composable
fun ControlsScreen() {
    var customControls by remember { mutableStateOf(true) }
    var gamepad by remember { mutableStateOf(true) }
    var touchInput by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.glass().padding(16.dp)) {
            SectionHeader(text = "CONTROLS", iconRes = R.drawable.ic_luc_gamepad)
            SettingSwitch("Enable custom controls", "On-screen button layout", customControls) { customControls = it }
            SettingSwitch("Gamepad support", "Emulate mouse/keyboard with a pad", gamepad) { gamepad = it }
            SettingSwitch("Touch character input", "Experimental char sender", touchInput) { touchInput = it }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { LaunchBackend.openControlEditor() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A00E5FF), contentColor = LucAccent),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("OPEN LAYOUT EDITOR", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LucTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = LucTextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LucAccent,
                checkedThumbColor = LucOnAccent,
                uncheckedTrackColor = Color(0xFF1F2937),
                uncheckedThumbColor = LucTextMuted
            )
        )
    }
}

/* ═══════════════════════ SKINS ═══════════════════════ */

@Composable
fun SkinsScreen(vm: LuciferViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.glass().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionHeader(text = "SKIN MANAGER", iconRes = R.drawable.ic_luc_shirt)
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LucAccent, RoundedCornerShape(16.dp))
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.avatar_apex),
                    contentDescription = "Current skin",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("APEX_GAMER", color = LucTextPrimary, fontWeight = FontWeight.Bold)
            Text("Ely.by session · TODO: real auth", color = LucTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    iconRes = R.drawable.ic_luc_shirt,
                    label = "Upload",
                    onClick = { vm.showNotice(R.string.lucifer_stub_notice) }
                )
                SecondaryActionButton(
                    iconRes = R.drawable.ic_luc_edit,
                    label = "Editor",
                    onClick = { vm.showNotice(R.string.lucifer_stub_notice) }
                )
            }
        }
    }
}

/* ═══════════════════════ SETTINGS ═══════════════════════ */

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var verifyAssets by remember { mutableStateOf(true) }
    var verboseLogs by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.glass().padding(16.dp)) {
            SectionHeader(text = "LAUNCHER SETTINGS", iconRes = R.drawable.ic_luc_gear)
            SettingSwitch("Verify assets on launch", "Checksum client jars each boot", verifyAssets) { verifyAssets = it }
            SettingSwitch("Verbose Java logs", "Pipe JVM stdout to the log view", verboseLogs) { verboseLogs = it }
            Spacer(Modifier.height(6.dp))
            SectionHeader(text = "LEGACY", iconRes = R.drawable.ic_luc_folder)
            Button(
                onClick = {
                    // Escape hatch back to the classic XML launcher UI
                    context.startActivity(Intent(context, LauncherActivity::class.java))
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A00E5FF), contentColor = LucAccent),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("OPEN CLASSIC LAUNCHER UI", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Backend actions on this screen are TODO stubs (see LaunchBackend.kt).",
                color = LucTextMuted,
                fontSize = 10.sp
            )
        }
    }
}
