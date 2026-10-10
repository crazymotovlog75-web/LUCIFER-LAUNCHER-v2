package net.kdt.pojavlaunch.lucifer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import net.kdt.pojavlaunch.lucifer.ui.LuciferApp

/**
 * Entry point of the revamped LUCIFER launcher UI (Jetpack Compose).
 * Reached from TestStorageActivity once storage permissions are granted.
 * The classic XML UI remains reachable from Settings → "Open classic UI".
 */
class LuciferLauncherActivity : ComponentActivity() {

    private val viewModel: LuciferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LuciferApp(vm = viewModel)
        }
    }
}
