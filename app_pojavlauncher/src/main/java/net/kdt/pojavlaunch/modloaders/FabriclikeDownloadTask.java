package net.kdt.pojavlaunch.modloaders;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class FabriclikeDownloadTask implements Runnable, Tools.DownloaderFeedback{
    private final ModloaderDownloadListener mModloaderDownloadListener;
    private final FabriclikeUtils mUtils;
    private final String mGameVersion;
    private final String mLoaderVersion;
    private final boolean mCreateProfile;
    private net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper mProgressWrapper;
    public FabriclikeDownloadTask(ModloaderDownloadListener modloaderDownloadListener, FabriclikeUtils utils, String mGameVersion, String mLoaderVersion, boolean mCreateProfile) {
        this.mModloaderDownloadListener = modloaderDownloadListener;
        this.mUtils = utils;
        this.mGameVersion = mGameVersion;
        this.mLoaderVersion = mLoaderVersion;
        this.mCreateProfile = mCreateProfile;
    }

    @Override
    public void run() {
        android.util.Log.d("FabricInstall", "STEP 5: Downloading loader");
        mProgressWrapper = new net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper(R.string.fabric_dl_progress, ProgressLayout.INSTALL_MODPACK);
        mProgressWrapper.extraString = mUtils.getName();
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.fabric_dl_progress, mUtils.getName());
        try {
            if(runCatching()) mModloaderDownloadListener.onDownloadFinished(null);
            else mModloaderDownloadListener.onDataNotAvailable();
        }catch (IOException e) {
            mModloaderDownloadListener.onDownloadError(e);
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    private boolean runCatching() throws IOException{
        String fabricJson = DownloadUtils.downloadString(mUtils.createJsonDownloadUrl(mGameVersion, mLoaderVersion));
        String versionId;
        try {
            JSONObject fabricJsonObject = new JSONObject(fabricJson);
            versionId = fabricJsonObject.getString("id");
        }catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        File versionJsonDir = new File(Tools.DIR_HOME_VERSION, versionId);
        File versionJsonFile = new File(versionJsonDir, versionId+".json");
        FileUtils.ensureDirectory(versionJsonDir);
        Tools.write(versionJsonFile.getAbsolutePath(), fabricJson);
        
        // A loader install from the instance-creation screen creates a new profile. Never mutate
        // or remove an existing version entry here: each install must remain independently
        // selectable, even when another profile uses the same Minecraft release.
        if (mCreateProfile) {
            LauncherProfiles.load();
            MinecraftProfile fabricProfile = new MinecraftProfile();
            fabricProfile.lastVersionId = versionId;
            fabricProfile.name = createUniqueProfileName();
            fabricProfile.icon = mUtils.getIconName();
            fabricProfile.type = "custom";
            fabricProfile.created = MinecraftProfile.currentTimestamp();

            String profileKey = LauncherProfiles.getFreeProfileKey();
            LauncherProfiles.mainProfileJson.profiles.put(profileKey, fabricProfile);
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                    .apply();
            LauncherProfiles.write();
        }
        return true;
    }

    private String createUniqueProfileName() {
        String baseName = "Minecraft " + mGameVersion;
        if (isProfileNameInUse(baseName)) {
            baseName += " (" + mUtils.getName() + " " + mLoaderVersion + ")";
        }
        String candidate = baseName;
        int suffix = 2;
        while (isProfileNameInUse(candidate)) {
            candidate = baseName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private boolean isProfileNameInUse(String name) {
        if (LauncherProfiles.mainProfileJson == null ||
                LauncherProfiles.mainProfileJson.profiles == null) return false;
        for (MinecraftProfile profile : LauncherProfiles.mainProfileJson.profiles.values()) {
            if (profile != null && profile.name != null && name.equalsIgnoreCase(profile.name.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateProgress(int curr, int max) {
        if (mProgressWrapper != null) mProgressWrapper.updateProgress(curr, max);
    }
}
