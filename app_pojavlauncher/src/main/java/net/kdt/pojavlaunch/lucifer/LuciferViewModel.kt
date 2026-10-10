package net.kdt.pojavlaunch.lucifer

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.lucifer.backend.LaunchBackend
import net.kdt.pojavlaunch.lucifer.data.InstanceInfo
import net.kdt.pojavlaunch.lucifer.data.LaunchProgress
import net.kdt.pojavlaunch.lucifer.data.QuickAction
import net.kdt.pojavlaunch.lucifer.data.ServerInfo

/**
 * Single source of UI state for the LUCIFER launcher home.
 * Real backend work is delegated to [LaunchBackend].
 */
class LuciferViewModel : ViewModel() {

    companion object {
        private const val TAG = "LuciferVM"
        private const val PING_INTERVAL_MS = 4000L
    }

    // ── Servers (simulated ping) ────────────────────────────────────────────
    private val _servers = MutableStateFlow(
        listOf(
            ServerInfo("BlockRealm", "play.blockrealm.net", 156, 500, 42, online = true),
            ServerInfo("HyperMC", "mc.hypermc.gg", 243, 1000, 58, online = true),
            ServerInfo("SkyLands", "skylands.fun", 87, 300, 71, online = true)
        )
    )
    val servers: StateFlow<List<ServerInfo>> = _servers.asStateFlow()

    // ── Instances: real profiles, spec mock list only as empty-state preview ──
    private val _instances = MutableStateFlow<List<InstanceInfo>>(emptyList())
    val instances: StateFlow<List<InstanceInfo>> = _instances.asStateFlow()

    private val _currentInstance = MutableStateFlow("MODDED OVERWORLD")
    val currentInstance: StateFlow<String> = _currentInstance.asStateFlow()

    // ── Launch pipeline ─────────────────────────────────────────────────────
    private val _launchProgress = MutableStateFlow<LaunchProgress?>(null)
    val launchProgress: StateFlow<LaunchProgress?> = _launchProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow("READY")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ── Dialogs / notices ───────────────────────────────────────────────────
    private val _dialogAction = MutableStateFlow<QuickAction?>(null)
    val dialogAction: StateFlow<QuickAction?> = _dialogAction.asStateFlow()

    /** String resource id of a pending notice dialog (launch errors, stubs). */
    private val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    init {
        loadInstances()
        startPingSimulation()
    }

    /** Load real installed profiles; fall back to the design-preview list. */
    fun loadInstances() {
        val real = LaunchBackend.installedInstances()
        if (real.isNotEmpty()) {
            _instances.value = real
            // Sync the hero title with the launcher-wide current profile
            val currentName = LaunchBackend.currentInstanceName() ?: real.firstOrNull()?.name
            if (currentName != null) _currentInstance.value = currentName.uppercase()
        } else {
            _instances.value = listOf(
                InstanceInfo("Modded Overworld", "1.21.4", "Fabric"),
                InstanceInfo("Vanilla Survival", "1.21.4", "None"),
                InstanceInfo("Winter SMP", "1.20.1", "Paper")
            )
        }
    }

    /** Mock server ping: player counts / latency drift every few seconds. */
    private fun startPingSimulation() {
        viewModelScope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                _servers.value = _servers.value.map { s ->
                    if (!s.online) return@map s
                    s.copy(
                        players = (s.players + Random.nextInt(-6, 9)).coerceIn(0, s.maxPlayers),
                        pingMs = LaunchBackend.pingServer(s.address)
                    )
                }
            }
        }
    }

    /**
     * PLAY: short pre-flight animation, then the REAL download/launch
     * pipeline via [LaunchBackend.launchInstance].
     */
    fun play(activity: Activity) {
        if (_launchProgress.value != null) return
        viewModelScope.launch {
            _statusMessage.value = "LAUNCHING"
            val steps = listOf(
                "Verifying instance files…" to 0.15f,
                "Resolving profile & account…" to 0.40f,
                "Starting download pipeline…" to 0.75f,
                "Handing over to JVM…" to 1.00f
            )
            for ((step, fraction) in steps) {
                _launchProgress.value = LaunchProgress(step, fraction)
                delay(450)
            }
            _launchProgress.value = null
            when (val result = LaunchBackend.launchInstance(activity, _currentInstance.value)) {
                is LaunchBackend.LaunchResult.Started -> {
                    // ContextAwareDoneListener will finish us once the game boots
                    _statusMessage.value = "GAME STARTING"
                }
                is LaunchBackend.LaunchResult.Error -> {
                    _statusMessage.value = "READY"
                    _notice.value = result.messageRes
                }
            }
        }
    }

    fun selectInstance(context: Context, name: String) {
        LaunchBackend.selectInstance(context, name)
        _currentInstance.value = name.uppercase()
        Log.i(TAG, "Instance selected: $name")
    }

    fun openQuickAction(action: QuickAction) {
        _dialogAction.value = action
        Log.i(TAG, "Quick action: ${action.label}")
    }

    fun dismissDialog() {
        _dialogAction.value = null
    }

    /** RUN button of the quick-action dialog: real action where wired. */
    fun runQuickAction(action: QuickAction, activity: Activity) {
        _dialogAction.value = null
        when (action) {
            QuickAction.CONTROLS -> LaunchBackend.openControlEditor(activity)
            QuickAction.LOGS -> LaunchBackend.shareLogs(activity)
            QuickAction.JAR -> LaunchBackend.executeJar(activity)
            QuickAction.MODS -> LaunchBackend.openModStore()
        }
    }

    fun showNotice(messageRes: Int) {
        _notice.value = messageRes
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Re-roll simulated ping values (server row tap / refresh). */
    fun refreshServers() {
        viewModelScope.launch {
            _servers.value = _servers.value.map { s ->
                s.copy(pingMs = LaunchBackend.pingServer(s.address))
            }
        }
    }
}
