package net.kdt.pojavlaunch.modloaders;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads a validated Fabric/Quilt profile off the UI thread and stores it atomically. */
public class FabriclikeDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private static final String TAG = "FabricInstall";
    private static final Set<String> ACTIVE_INSTALLS = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
    private static final Object PROFILE_WRITE_LOCK = new Object();

    private final ModloaderDownloadListener mModloaderDownloadListener;
    private final FabriclikeUtils mUtils;
    private final String mGameVersion;
    private final String mLoaderVersion;
    private final boolean mCreateProfile;
    private net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper mProgressWrapper;

    public FabriclikeDownloadTask(ModloaderDownloadListener listener, FabriclikeUtils utils,
                                  String gameVersion, String loaderVersion, boolean createProfile) {
        mModloaderDownloadListener = listener;
        mUtils = utils;
        mGameVersion = gameVersion;
        mLoaderVersion = loaderVersion;
        mCreateProfile = createProfile;
    }

    @Override
    public void run() {
        String installKey = mUtils.getName() + "\u0000" + mGameVersion + "\u0000" + mLoaderVersion;
        if (!ACTIVE_INSTALLS.add(installKey)) {
            mModloaderDownloadListener.onDownloadError(
                    new IOException(mUtils.getName() + " is already being installed for this version."));
            return;
        }

        try {
            Log.d(TAG, "Starting " + mUtils.getName() + " install for Minecraft " + mGameVersion);
            mProgressWrapper = new net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper(
                    R.string.fabric_dl_progress, ProgressLayout.INSTALL_MODPACK);
            mProgressWrapper.extraString = mUtils.getName();
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0,
                    R.string.fabric_dl_progress, mUtils.getName());
            runCatching();
            mModloaderDownloadListener.onDownloadFinished(null);
        } catch (Exception error) {
            Log.e(TAG, mUtils.getName() + " installation failed", error);
            mModloaderDownloadListener.onDownloadError(error);
        } finally {
            ACTIVE_INSTALLS.remove(installKey);
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private void runCatching() throws IOException {
        String profileJson = mUtils.downloadProfileJson(mGameVersion, mLoaderVersion);
        final String versionId;
        try {
            JSONObject profile = new JSONObject(profileJson);
            versionId = profile.getString("id").trim();
            if (versionId.isEmpty() || !versionId.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")) {
                throw new IOException("The " + mUtils.getName() + " profile has an unsafe version id");
            }
        } catch (JSONException e) {
            throw new IOException("The " + mUtils.getName() + " profile could not be read", e);
        }

        final String profileKey;
        final MinecraftProfile targetProfile;
        final boolean addProfile;
        synchronized (PROFILE_WRITE_LOCK) {
            LauncherProfiles.load();
            if (mCreateProfile) {
                String existingProfileKey = findProfileForVersion(versionId);
                if (existingProfileKey != null) {
                    profileKey = existingProfileKey;
                    targetProfile = LauncherProfiles.mainProfileJson.profiles.get(existingProfileKey);
                    addProfile = false;
                } else {
                    MinecraftProfile selectedProfile = LauncherProfiles.getCurrentProfile();
                    String newProfileKey = LauncherProfiles.getFreeProfileKey();
                    String newInstanceId = net.kdt.pojavlaunch.instances.InstancePathResolver
                            .instanceIdForProfileKey(newProfileKey);
                    MinecraftProfile fabricProfile = new MinecraftProfile(selectedProfile);
                    fabricProfile.lastVersionId = versionId;
                    fabricProfile.name = createUniqueProfileName();
                    fabricProfile.icon = mUtils.getIconName();
                    fabricProfile.type = "custom";
                    fabricProfile.created = MinecraftProfile.currentTimestamp();
                    fabricProfile.lastUsed = null;
                    fabricProfile.isDefault = false;
                    fabricProfile.instanceId = newInstanceId;
                    fabricProfile.gameDir = net.kdt.pojavlaunch.instances.InstancePathResolver
                            .gameDirectoryValue(newInstanceId);
                    try {
                        Tools.getInstanceManager().duplicateInstance(selectedProfile.instanceId,
                                newInstanceId, newProfileKey, fabricProfile.name, versionId);
                    } catch (IllegalArgumentException | IOException error) {
                        throw new IOException("Could not create an isolated " + mUtils.getName() + " instance", error);
                    }
                    profileKey = newProfileKey;
                    targetProfile = fabricProfile;
                    addProfile = true;
                }
            } else {
                profileKey = LauncherPreferences.DEFAULT_PREF.getString(
                        LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                targetProfile = LauncherProfiles.getCurrentProfile();
                addProfile = false;
            }
        }

        File versionJsonDir = Tools.getVersionDirectory(targetProfile, versionId);
        if (!versionJsonDir.isDirectory() && !versionJsonDir.mkdirs()) {
            throw new IOException("Unable to create the instance version directory for " + mUtils.getName());
        }
        File versionJsonFile = new File(versionJsonDir, versionId + ".json");
        writeAtomically(versionJsonFile, profileJson);

        // Add a selectable profile only once per exact loader version. The JSON/JAR path is always
        // under that profile's instance root; global .minecraft/versions is read-only migration input.
        if (mCreateProfile) {
            synchronized (PROFILE_WRITE_LOCK) {
                if (addProfile) {
                    LauncherProfiles.mainProfileJson.profiles.put(profileKey, targetProfile);
                    LauncherProfiles.write();
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                        .apply();
            }
        }
    }

    private String findProfileForVersion(String versionId) {
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) return null;
        for (java.util.Map.Entry<String, MinecraftProfile> entry :
                LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile != null && versionId.equals(profile.lastVersionId)) return entry.getKey();
        }
        return null;
    }

    private void writeAtomically(File destination, String content) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || !parent.isDirectory()) throw new IOException("Version directory is unavailable");
        File temporary = File.createTempFile(destination.getName() + ".", ".tmp", parent);
        boolean moved = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }
            try {
                Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
                moved = true;
            } catch (ErrnoException e) {
                throw new IOException("Unable to save the " + mUtils.getName() + " profile", e);
            }
        } finally {
            if (!moved && temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Could not remove temporary profile file " + temporary.getAbsolutePath());
            }
        }
    }

    private String createUniqueProfileName() {
        String baseName = "Minecraft " + mGameVersion;
        if (isProfileNameInUse(baseName)) {
            baseName += " (" + mUtils.getName() + " " + mLoaderVersion + ")";
        }
        String candidate = baseName;
        int suffix = 2;
        while (isProfileNameInUse(candidate)) candidate = baseName + " (" + suffix++ + ")";
        return candidate;
    }

    private boolean isProfileNameInUse(String name) {
        if (LauncherProfiles.mainProfileJson == null ||
                LauncherProfiles.mainProfileJson.profiles == null) return false;
        for (MinecraftProfile profile : LauncherProfiles.mainProfileJson.profiles.values()) {
            if (profile != null && profile.name != null && name.equalsIgnoreCase(profile.name.trim())) return true;
        }
        return false;
    }

    @Override
    public void updateProgress(int current, int max) {
        if (mProgressWrapper != null) mProgressWrapper.updateProgress(current, max);
    }
}
