package net.kdt.pojavlaunch.modloaders;

import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.InstanceManager;
import net.kdt.pojavlaunch.instances.InstancePathResolver;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BTADownloadTask implements Runnable {
    private static final String BASE_JSON = "{\"inheritsFrom\":\"b1.7.3\",\"mainClass\":\"net.minecraft.client.Minecraft\",\"libraries\":[{\"name\":\"bta-client:bta-client:%1$s\",\"downloads\":{\"artifact\":{\"path\":\"bta-client/bta-client-%1$s.jar\",\"url\":\"%2$s\"}}}],\"id\":\"%3$s\"}";
    private final ModloaderDownloadListener mListener;
    private final BTAUtils.BTAVersion mBtaVersion;

    public BTADownloadTask(ModloaderDownloadListener mListener, BTAUtils.BTAVersion mBtaVersion) {
        this.mListener = mListener;
        this.mBtaVersion = mBtaVersion;
    }

    @Override
    public void run() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.fabric_dl_progress, "BTA");
        try {
            runCatching() ;
            mListener.onDownloadFinished(null);
        }catch (IOException e) {
            mListener.onDownloadError(e);
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    private String tryDownloadIcon() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Base64OutputStream base64OutputStream = new Base64OutputStream(byteArrayOutputStream, Base64.DEFAULT)){
            // Instead of appending and wasting memory with a StringBuilder, just write the prefix
            // to the stream before the base64 icon data.
            byteArrayOutputStream.write("data:image/png;base64,".getBytes(StandardCharsets.US_ASCII));
            DownloadUtils.download(mBtaVersion.iconUrl, base64OutputStream);
            return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.US_ASCII);
        }catch (IOException e) {
            Log.w("BTADownloadTask", "Failed to download base64 icon", e);
        }finally {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                Log.wtf("BTADownloadTask", "Failed to close a byte array stream??", e);
            }
        }
        return null;
    }

    private void createJson(String btaVersionId, MinecraftProfile profile) throws IOException {
        String btaJson = String.format(BASE_JSON, mBtaVersion.versionName, mBtaVersion.downloadUrl, btaVersionId);
        File jsonDir = Tools.getVersionDirectory(profile, btaVersionId);
        File jsonFile = new File(jsonDir, btaVersionId + ".json");
        FileUtils.ensureDirectory(jsonDir);
        Tools.write(jsonFile.getAbsolutePath(), btaJson);
    }

    // BTA does not publish SHA1 hashes, so clear only its client artifact to force a fresh download.
    private void removeOldClient(MinecraftProfile profile) throws IOException {
        File btaClientPath = new File(Tools.getLibrariesRoot(profile),
                String.format("bta-client/bta-client-%1$s.jar", mBtaVersion.versionName));
        if (btaClientPath.exists() && !btaClientPath.delete()) {
            throw new IOException("Failed to delete old BTA client jar");
        }
    }

    private MinecraftProfile getOrCreateProfile(String btaVersionId) throws IOException {
        LauncherProfiles.load();
        for (java.util.Map.Entry<String, MinecraftProfile> entry :
                LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile != null && "Better than Adventure!".equalsIgnoreCase(profile.name)) {
                profile.lastVersionId = btaVersionId;
                profile.type = "modpack";
                if (profile.instanceId == null || profile.instanceId.trim().isEmpty()) {
                    profile.instanceId = InstancePathResolver.instanceIdForProfileKey(entry.getKey());
                }
                profile.gameDir = InstancePathResolver.gameDirectoryValue(profile.instanceId);
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, entry.getKey())
                        .apply();
                return profile;
            }
        }

        String profileKey = LauncherProfiles.getFreeProfileKey();
        String instanceId = InstancePathResolver.instanceIdForProfileKey(profileKey);
        MinecraftProfile selectedProfile = LauncherProfiles.getCurrentProfile();
        MinecraftProfile btaProfile = new MinecraftProfile(selectedProfile);
        btaProfile.lastVersionId = btaVersionId;
        btaProfile.name = "Better than Adventure!";
        btaProfile.type = "modpack";
        btaProfile.icon = tryDownloadIcon();
        btaProfile.created = MinecraftProfile.currentTimestamp();
        btaProfile.lastUsed = null;
        btaProfile.isDefault = false;
        btaProfile.instanceId = instanceId;
        btaProfile.gameDir = InstancePathResolver.gameDirectoryValue(instanceId);

        InstanceManager manager = Tools.getInstanceManager();
        File legacyBtaDirectory = manager.paths().resolveLegacyGameDirectory(
                "./custom_instances/better_than_adventure");
        if (legacyBtaDirectory.isDirectory()) {
            manager.importLegacyInstance(instanceId, profileKey, btaProfile.name,
                    btaVersionId, legacyBtaDirectory);
        } else {
            manager.duplicateInstance(selectedProfile.instanceId, instanceId, profileKey,
                    btaProfile.name, btaVersionId);
        }
        LauncherProfiles.mainProfileJson.profiles.put(profileKey, btaProfile);
        LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
        return btaProfile;
    }

    public void runCatching() throws IOException {
        String btaVersionId = "bta-" + mBtaVersion.versionName;
        MinecraftProfile profile = getOrCreateProfile(btaVersionId);
        createJson(btaVersionId, profile);
        removeOldClient(profile);
        profile.lastVersionId = btaVersionId;
        profile.icon = tryDownloadIcon();
        String profileKey = "bta";
        for (java.util.Map.Entry<String, MinecraftProfile> entry :
                LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (entry.getValue() == profile) {
                profileKey = entry.getKey();
                break;
            }
        }
        Tools.getInstanceManager().updateMetadata(profile.instanceId, profileKey,
                profile.name, profile.lastVersionId);
        LauncherProfiles.write();
    }
}
