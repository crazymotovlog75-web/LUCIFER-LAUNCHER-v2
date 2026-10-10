package net.kdt.pojavlaunch.lucifer.data

import net.kdt.pojavlaunch.R

/**
 * Sidebar navigation destinations. Each one swaps the CENTER pane content
 * (see LuciferApp.kt NavHost) while the sidebar / right pane stay persistent.
 */
enum class NavDestination(val route: String, val label: String, val iconRes: Int) {
    HOME("home", "Home", R.drawable.ic_luc_home),
    INSTANCES("instances", "Instances", R.drawable.ic_luc_cube),
    MOD_STORE("mod_store", "Mod Store", R.drawable.ic_luc_store),
    CONTROLS("controls", "Controls", R.drawable.ic_luc_gamepad),
    SKINS("skins", "Skins", R.drawable.ic_luc_shirt),
    SETTINGS("settings", "Settings", R.drawable.ic_luc_gear);

    companion object {
        fun fromRoute(route: String?): NavDestination =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}

/** Mock server entry shown in the right pane (ping is simulated). */
data class ServerInfo(
    val name: String,
    val address: String,
    val players: Int,
    val maxPlayers: Int,
    val pingMs: Int,
    val online: Boolean
)

/** Quick-action tiles of the right pane (2x2 grid). */
enum class QuickAction(val label: String, val iconRes: Int, val description: String) {
    MODS("Mods", R.drawable.ic_luc_puzzle, "Opens the mod manager for the current instance."),
    CONTROLS("Controls", R.drawable.ic_luc_gamepad, "Opens the on-screen control layout editor."),
    LOGS("Logs", R.drawable.ic_luc_doc, "Shows / shares the latest game log."),
    JAR("Jar", R.drawable.ic_luc_jar, "Executes a standalone .jar with the instance JRE.")
}

/** Installable instance shown on the Instances screen. */
data class InstanceInfo(
    val name: String,
    val version: String,
    val loader: String
)

/** Transient state of the (stubbed) launch pipeline. */
data class LaunchProgress(val step: String, val fraction: Float)
