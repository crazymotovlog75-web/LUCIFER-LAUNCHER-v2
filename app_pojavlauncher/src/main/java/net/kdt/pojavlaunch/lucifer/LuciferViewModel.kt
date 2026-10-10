package net.kdt.pojavlaunch.lucifer

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
 * Backend interactions are delegated to [LaunchBackend] (all TODO stubs).
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

    // ── Instances ────────────────────────────────────────────────────────────
    private val _instances = MutableStateFlow(
        listOf(
            InstanceInfo("Modded Overworld", "1.21.4", "Fabric"),
            InstanceInfo("Vanilla Survival", "1.21.4", "None"),
            InstanceInfo("Winter SMP", "1.20.1", "Paper")
        )
    )
    val instances: StateFlow<List<InstanceInfo>> = _instances.asStateFlow()

    private val _currentInstance = MutableStateFlow("MODDED OVERWORLD")
    val currentInstance: StateFlow<String> = _currentInstance.asStateFlow()

    // ── Launch pipeline (mock loading state) ────────────────────────────────
    private val _launchProgress = MutableStateFlow<LaunchProgress?>(null)
    val launchProgress: StateFlow<LaunchProgress?> = _launchProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow("READY")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ── Quick-action dialog ──────────────────────────────────────────────────
    private val _dialogAction = MutableStateFlow<QuickAction?>(null)
    val dialogAction: StateFlow<QuickAction?> = _dialogAction.asStateFlow()

    init {
        startPingSimulation()
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

    /** PLAY button: mock loading sequence, then hands over to the stub bed. */
    fun play() {
        if (_launchProgress.value != null) return
        viewModelScope.launch {
            _statusMessage.value = "LAUNCHING"
            val steps = listOf(
                "Verifying instance files…" to 0.15f,
                "Downloading assets… (TODO)" to 0.40f,
                "Injecting authlib… (TODO)" to 0.70f,
                "Starting JVM… (TODO)" to 1.00f
            )
            for ((step, fraction) in steps) {
                _launchProgress.value = LaunchProgress(step, fraction)
                delay(850)
            }
            _launchProgress.value = null
            _statusMessage.value = "READY"
            LaunchBackend.launchInstance(_currentInstance.value)
        }
    }

    fun selectInstance(name: String) {
        _currentInstance.value = name.uppercase()
        Log.i(TAG, "Instance selected: $name")
    }

    fun openQuickAction(action: QuickAction) {
        _dialogAction.value = action
        Log.i(TAG, "Quick action: ${action.label} — TODO: wire to backend")
    }

    fun dismissDialog() {
        _dialogAction.value = null
    }

    fun refreshServers() {
        viewModelScope.launch {
            _servers.value = _servers.value.map { s ->
                s.copy(pingMs = LaunchBackend.pingServer(s.address))
            }
        }
    }
}
