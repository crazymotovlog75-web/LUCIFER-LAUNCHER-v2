package net.kdt.pojavlaunch.lucifer.backend

import android.util.Log
import kotlin.random.Random

/**
 * Stub bed for the LUCIFER Compose UI.
 *
 * Every function here is a clearly marked TODO placeholder: the UI layer is
 * 100% functional against these signatures, but no real work (JVM exec,
 * asset download, ICMP ping, CurseForge calls…) is performed yet.
 *
 * When wiring the real backend, delegate to the existing PojavLauncher
 * pipeline (Tools, JMinecraftVersionList, Downloader, etc.).
 */
object LaunchBackend {

    private const val TAG = "LuciferBackend"

    /**
     * TODO: replace with the real launch pipeline
     *  (net.kdt.pojavlaunch.Tools#launchMinecraft / MinecraftGLSurface boot).
     */
    fun launchInstance(instanceName: String) {
        Log.i(TAG, "STUB launchInstance($instanceName) — TODO: boot JVM + LWJGL here")
    }

    /**
     * TODO: replace with real asset/index download
     *  (net.kdt.pojavlaunch.downloader / JAssets verification).
     */
    fun downloadAssets(version: String) {
        Log.i(TAG, "STUB downloadAssets($version) — TODO: run asset downloader")
    }

    /**
     * TODO: replace with a real TCP/ICMP ping (ServerPingThread equivalent).
     * @return simulated latency in milliseconds.
     */
    fun pingServer(address: String): Int {
        Log.d(TAG, "STUB pingServer($address) — TODO: real MOTD/ping handshake")
        return Random.nextInt(15, 90)
    }

    /** TODO: replace with real ModStore (CurseForge/Modrinth) navigation. */
    fun openModStore() {
        Log.i(TAG, "STUB openModStore() — TODO: open mod repository UI")
    }

    /** TODO: replace with real control-editor launch (CustomControlsActivity). */
    fun openControlEditor() {
        Log.i(TAG, "STUB openControlEditor() — TODO: open CustomControlsActivity")
    }

    /** TODO: replace with real log viewer / share sheet. */
    fun shareLogs() {
        Log.i(TAG, "STUB shareLogs() — TODO: build log share intent")
    }

    /** TODO: replace with real .jar executor (InstallJarActivity equivalent). */
    fun executeJar() {
        Log.i(TAG, "STUB executeJar() — TODO: pick + run .jar file")
    }

    /** TODO: replace with real skin upload / Ely.by manager. */
    fun manageSkins() {
        Log.i(TAG, "STUB manageSkins() — TODO: open skin manager")
    }

    /** TODO: replace with real directory picker (game dir intent). */
    fun openGameDirectory() {
        Log.i(TAG, "STUB openGameDirectory() — TODO: open game directory")
    }

    /** TODO: replace with real profile editor (fragment_profile_editor). */
    fun editProfile(instanceName: String) {
        Log.i(TAG, "STUB editProfile($instanceName) — TODO: open profile editor")
    }
}
