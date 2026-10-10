package net.kdt.pojavlaunch.lucifer.backend

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.text.ParseException
import kotlin.random.Random
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.PojavProfile
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener
import net.kdt.pojavlaunch.lucifer.data.InstanceInfo
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader
import net.kdt.pojavlaunch.tasks.MinecraftDownloader
import net.kdt.pojavlaunch.utils.DateUtils
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile

/**
 * Bridge between the LUCIFER Compose UI and the real PojavLauncher backend.
 *
 * REAL (fully wired):
 *  - [launchInstance]      → profile resolution + account guard + MinecraftDownloader
 *                            pipeline (same sequence as LauncherActivity.mLaunchGameListener)
 *  - [installedInstances]  → real profiles from launcher_profiles.json
 *  - [selectInstance]      → persists PREF_KEY_CURRENT_PROFILE
 *  - [shareLogs]           → Tools.shareLog (latestlog.txt share sheet)
 *  - [openGameDirectory]   → Tools.openPath on the profile game dir
 *  - [openControlEditor]   → CustomControlsActivity
 *  - [executeJar]          → Tools.installMod (jar executor picker)
 *
 * STILL STUBBED (TODO) — these features live as fragments inside the classic
 * XML UI and cannot be hosted from a Compose activity without a fragment
 * container; wire them when the classic screens are ported:
 *  - [openModStore], [manageSkins], [editProfile], [downloadAssets]
 */
object LaunchBackend {

    private const val TAG = "LuciferBackend"

    sealed class LaunchResult {
        object Started : LaunchResult()
        class Error(val messageRes: Int) : LaunchResult()
    }

    /**
     * Real launch pipeline — mirror of LauncherActivity.mLaunchGameListener.
     * On success the download pipeline ends in ContextAwareDoneListener,
     * which boots MainActivity (the game) and finishes this activity.
     */
    fun launchInstance(activity: Activity, instanceName: String): LaunchResult {
        if (ProgressKeeper.getTaskCount() > 0) {
            return LaunchResult.Error(R.string.tasks_ongoing)
        }
        LauncherProfiles.load()
        val profiles = LauncherProfiles.mainProfileJson?.profiles
        if (profiles == null || profiles.isEmpty()) {
            return LaunchResult.Error(R.string.error_no_version)
        }
        val fallbackKey = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
        val entry = profiles.entries.firstOrNull { it.value.name.equals(instanceName, ignoreCase = true) }
            ?: profiles.entries.firstOrNull { it.key == fallbackKey }
            ?: return LaunchResult.Error(R.string.error_no_version)
        val profile = entry.value
        if (profile.lastVersionId == null || "Unknown" == profile.lastVersionId) {
            return LaunchResult.Error(R.string.error_no_version)
        }
        val account = PojavProfile.getCurrentProfileContent(activity, null)
            ?: return LaunchResult.Error(R.string.no_saved_accounts)

        val normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(profile.lastVersionId)
        val mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId)

        // Demo accounts may only play releases newer than 1.3 (classic rule)
        if (account.isDemo) {
            var isOlderThan13 = true
            if (mcVersion != null) {
                try {
                    isOlderThan13 = DateUtils.dateBefore(
                        DateUtils.parseReleaseDate(mcVersion.releaseTime), 2012, 6, 22
                    )
                } catch (ignored: ParseException) { }
            }
            if (isOlderThan13) return LaunchResult.Error(R.string.demo_versions_supported)
        }

        // Make the picked instance the launcher-wide current profile so the
        // game process boots the correct one.
        LauncherPreferences.DEFAULT_PREF.edit()
            .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, entry.key)
            .apply()

        MinecraftDownloader().start(
            activity,
            mcVersion,
            normalizedVersionId,
            ContextAwareDoneListener(activity, normalizedVersionId)
        )
        Log.i(TAG, "launchInstance($instanceName): download pipeline started")
        return LaunchResult.Started
    }

    /** Real installed profiles; empty list when none exist yet. */
    fun installedInstances(): List<InstanceInfo> = try {
        LauncherProfiles.load()
        LauncherProfiles.mainProfileJson?.profiles?.values?.map { p ->
            InstanceInfo(
                name = p.name ?: "Unknown",
                version = p.lastVersionId ?: "?",
                loader = loaderOf(p)
            )
        } ?: emptyList()
    } catch (t: Throwable) {
        Log.w(TAG, "installedInstances: could not read profiles", t)
        emptyList()
    }

    private fun loaderOf(profile: MinecraftProfile): String {
        val id = (profile.lastVersionId ?: "").lowercase()
        return when {
            "neoforge" in id -> "NeoForge"
            "fabric" in id -> "Fabric"
            "forge" in id -> "Forge"
            "optifine" in id -> "OptiFine"
            else -> "None"
        }
    }

    /** Name of the launcher-wide current profile (PREF_KEY_CURRENT_PROFILE). */
    fun currentInstanceName(): String? = try {
        LauncherProfiles.load()
        val key = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
        LauncherProfiles.mainProfileJson?.profiles?.get(key)?.name
    } catch (t: Throwable) {
        null
    }

    /** Persist the launcher-wide current profile (classic UI respects it too). */
    fun selectInstance(context: Context, name: String) {
        LauncherProfiles.load()
        val entry = LauncherProfiles.mainProfileJson?.profiles?.entries
            ?.firstOrNull { it.value.name.equals(name, ignoreCase = true) } ?: return
        LauncherPreferences.DEFAULT_PREF.edit()
            .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, entry.key)
            .apply()
        Log.i(TAG, "Current profile set to: ${entry.key} ($name)")
    }

    /** Real log share sheet (latestlog.txt). */
    fun shareLogs(context: Context) {
        Tools.shareLog(context)
    }

    /** Real game-directory browser for the current profile. */
    fun openGameDirectory(context: Context) {
        LauncherProfiles.load()
        val current = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null)
        val profile = if (current != null) {
            LauncherProfiles.mainProfileJson?.profiles?.get(current)
        } else null
        val dir = if (profile != null) Tools.getGameDirPath(profile) else File(Tools.DIR_GAME_NEW)
        Tools.openPath(context, dir, false)
    }

    /** Real control-layout editor activity. */
    fun openControlEditor(context: Context) {
        context.startActivity(Intent(context, CustomControlsActivity::class.java))
    }

    /** Real .jar executor picker (same as classic "Execute .jar"). */
    fun executeJar(activity: Activity) {
        if (ProgressKeeper.getTaskCount() == 0) {
            Tools.installMod(activity, false)
        }
    }

    /**
     * Simulated latency while the server list stays mock data.
     * TODO: replace with a real TCP/MOTD ping handshake.
     */
    fun pingServer(address: String): Int {
        Log.d(TAG, "STUB pingServer($address) — TODO: real MOTD/ping handshake")
        return Random.nextInt(15, 90)
    }

    // ── Remaining TODO stubs (classic-UI fragment features) ─────────────────

    /** TODO: port ModsSearchFragment to Compose or host it in a fragment container. */
    fun openModStore() {
        Log.i(TAG, "STUB openModStore() — TODO: port ModsSearchFragment")
    }

    /** TODO: port SkinManagerFragment to Compose or host it in a fragment container. */
    fun manageSkins() {
        Log.i(TAG, "STUB manageSkins() — TODO: port SkinManagerFragment")
    }

    /** TODO: port the profile editor fragment (mcVersionSpinner.openProfileEditor). */
    fun editProfile(instanceName: String) {
        Log.i(TAG, "STUB editProfile($instanceName) — TODO: port profile editor fragment")
    }

    /** TODO: standalone asset pre-download UI (launch pipeline already downloads assets). */
    fun downloadAssets(version: String) {
        Log.i(TAG, "STUB downloadAssets($version) — TODO: standalone asset pre-fetch UI")
    }
}
