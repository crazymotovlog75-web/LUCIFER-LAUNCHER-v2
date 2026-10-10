package net.kdt.pojavlaunch.lucifer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.kdt.pojavlaunch.lucifer.LuciferViewModel
import net.kdt.pojavlaunch.lucifer.data.NavDestination
import net.kdt.pojavlaunch.lucifer.data.QuickAction
import net.kdt.pojavlaunch.lucifer.theme.LucAccent
import net.kdt.pojavlaunch.lucifer.theme.LucBackground
import net.kdt.pojavlaunch.lucifer.theme.LucTextMuted
import net.kdt.pojavlaunch.lucifer.theme.LuciferTheme

/**
 * Root shell of the LUCIFER launcher.
 *
 * Responsive strategy (keeps the 3-pane aesthetic on any landscape screen):
 *  - width >= 840dp : sidebar + center + right panes side by side
 *  - width >= 600dp : sidebar + center; right pane merges under the center
 *  - width <  600dp : icon-rail sidebar + single scrolling column
 */
@Composable
fun LuciferApp(vm: LuciferViewModel) {
    LuciferTheme {
        Surface(color = LucBackground, modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: NavDestination.HOME.route
            val dialogAction by vm.dialogAction.collectAsState()

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val threePane = maxWidth >= 840.dp
                val twoPane = maxWidth >= 600.dp

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(12.dp)
                ) {
                    SidebarPane(
                        currentRoute = currentRoute,
                        onNavigate = { dest ->
                            if (dest.route != currentRoute) {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        compact = !twoPane,
                        modifier = Modifier.width(if (threePane) 210.dp else if (twoPane) 190.dp else 64.dp)
                    )
                    Spacer(Modifier.width(12.dp))

                    if (threePane) {
                        CenterNavHost(
                            vm = vm,
                            navController = navController,
                            embeddedRightPane = null,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        RightPane(vm = vm, modifier = Modifier.weight(0.72f))
                    } else {
                        // Right pane content is embedded into each screen
                        CenterNavHost(
                            vm = vm,
                            navController = navController,
                            embeddedRightPane = { RightPane(vm = vm) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            dialogAction?.let { action ->
                QuickActionDialog(action = action, onDismiss = vm::dismissDialog)
            }
        }
    }
}

/** Center pane NavHost — one route per sidebar destination. */
@Composable
private fun CenterNavHost(
    vm: LuciferViewModel,
    navController: androidx.navigation.NavHostController,
    embeddedRightPane: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavDestination.HOME.route,
        modifier = modifier
    ) {
        composable(NavDestination.HOME.route) {
            HomeScreen(vm = vm, embeddedRightPane = { embeddedRightPane?.invoke() })
        }
        composable(NavDestination.INSTANCES.route) { InstancesScreen(vm) }
        composable(NavDestination.MOD_STORE.route) { ModStoreScreen() }
        composable(NavDestination.CONTROLS.route) { ControlsScreen() }
        composable(NavDestination.SKINS.route) { SkinsScreen() }
        composable(NavDestination.SETTINGS.route) { SettingsScreen() }
    }
}

/** Mock dialog shown when a quick action tile is tapped. */
@Composable
private fun QuickActionDialog(action: QuickAction, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = null,
                tint = LucAccent
            )
        },
        title = { Text(action.label.uppercase()) },
        text = {
            Text(
                action.description + "\n\nThis action is a UI stub — wiring lives in LaunchBackend (TODO)."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = LucAccent) }
        },
        containerColor = LucBackground,
        titleContentColor = LucAccent,
        textContentColor = LucTextMuted,
        iconContentColor = LucAccent
    )
}
