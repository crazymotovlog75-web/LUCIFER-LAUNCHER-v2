package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModpackInstaller {

    public static ModLoader installModpack(ModDetail modDetail, int selectedVersion, InstallFunction installFunction) throws IOException {
        String versionUrl = modDetail.versionUrls[selectedVersion];
        String versionHash = modDetail.versionHashes[selectedVersion];
        String modpackName = (modDetail.title.toLowerCase(Locale.ROOT) + " " + modDetail.versionNames[selectedVersion])
                .trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_" );
        if (versionHash != null) {
            modpackName += "_" + versionHash;
        }
        if (modpackName.length() > 255){
            modpackName = modpackName.substring(0,255);
        }

        LauncherProfiles.load();
        String profileKey = uniqueProfileKey(modpackName);
        String instanceId = newInstanceId();
        net.kdt.pojavlaunch.instances.InstanceManager instanceManager = Tools.getInstanceManager();
        File instanceRoot = instanceManager.initializeInstance(instanceId, profileKey, modDetail.title, null);

        // Get the modpack file
        File modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf"); // Cache File
        ModLoader modLoaderInfo;
        try {
            byte[] downloadBuffer = new byte[8192];
            DownloadUtils.ensureSha1(modpackFile, versionHash, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(versionUrl, modpackFile, downloadBuffer,
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });

            // Pack contents and overrides are extracted directly into this managed gameDir.
            modLoaderInfo = installFunction.installModpack(modpackFile, instanceRoot);
        } catch (IOException | RuntimeException error) {
            instanceManager.deleteInstance(instanceId);
            throw error;
        } finally {
            modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        if(modLoaderInfo == null) {
            instanceManager.deleteInstance(instanceId);
            return null;
        }
        modLoaderInfo.targetGameDirectory = instanceRoot.getAbsolutePath();
        registerProfile(profileKey, instanceId, modDetail.title, modLoaderInfo,
                ModIconCache.getBase64Image(modDetail.getIconCacheTag()));
        return modLoaderInfo;
    }

    public static ModLoader importModpack(Activity activity, Uri zipUri, InstallFunction installFunction) throws IOException, NoSuchAlgorithmException {
        String modrinthPackInfoFileName = "modrinth.index.json";
        String curseforgePackInfoFileName = "manifest.json";
        InputStream inputStream = null;
        inputStream = activity.getContentResolver().openInputStream(zipUri);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            boolean isModrinth = zipEntry.getName().equals(modrinthPackInfoFileName);
            boolean isCurseforge = zipEntry.getName().equals(curseforgePackInfoFileName);
            if (!(isModrinth || isCurseforge)) continue;
            // Read Manifest JSON
            BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));
            String str;
            StringBuilder jsonString = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                jsonString.append(str).append("\n");
            }
            zipInputStream.close();

            // Hash the ZIP File
            inputStream = activity.getContentResolver().openInputStream(zipUri);
            MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
            DigestInputStream hashingStream = new DigestInputStream(inputStream, algorithm);

            byte[] buffer = new byte[8192];
            while (hashingStream.read(buffer) != -1) {} // just read to update the digest
            hashingStream.close();
            byte[] digest = algorithm.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String hash = sb.toString();

            // Parse the JSON to prepare for instance creation
            JsonObject packInfoJson = JsonParser.parseString(jsonString.toString()).getAsJsonObject();
            String modpackName;
            if(isModrinth){
                // Added a for because there is an awkward __ that I can't be bothered to fix
                // FO only deduplication be like:
                modpackName = (packInfoJson.get("name").getAsString().toLowerCase(Locale.ROOT) +
                        packInfoJson.get("versionId") + "for" +
                        packInfoJson.get("dependencies").getAsJsonObject().get("minecraft"));
            } else {
                modpackName = (packInfoJson.get("name").getAsString().toLowerCase(Locale.ROOT) +
                        packInfoJson.get("version") + "for" +
                        packInfoJson.get("minecraft").getAsJsonObject().get("version"));
            }
            modpackName = modpackName.trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_");
            modpackName = modpackName + hash;

            // Copy ZIP file to cache
            if(modpackName == null) throw new IOException("Corrupt Modpack manifest file.");
            File modpackFile = null;
            modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf");
            inputStream = activity.getContentResolver().openInputStream(zipUri);
            FileOutputStream output = new FileOutputStream(modpackFile);
            byte[] b = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(b)) != -1) {
                output.write(b, 0, read);
            }
            output.flush();

            LauncherProfiles.load();
            String profileKey = uniqueProfileKey(modpackName);
            String displayName = packInfoJson.get("name").getAsString();
            String instanceId = newInstanceId();
            net.kdt.pojavlaunch.instances.InstanceManager instanceManager = Tools.getInstanceManager();
            File instanceRoot = instanceManager.initializeInstance(instanceId, profileKey, displayName, null);
            ModLoader modLoaderInfo;
            try {
                // Install the actual pack directly into its managed instance gameDir.
                modLoaderInfo = installFunction.installModpack(modpackFile, instanceRoot);
            } catch (IOException | RuntimeException error) {
                instanceManager.deleteInstance(instanceId);
                throw error;
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                modpackFile.delete();
            }
            if(modLoaderInfo == null) {
                instanceManager.deleteInstance(instanceId);
                return null;
            }

            // Create the instance (We don't have a picture guys)
            modLoaderInfo.targetGameDirectory = instanceRoot.getAbsolutePath();
            registerProfile(profileKey, instanceId, displayName, modLoaderInfo, null);
            return modLoaderInfo;
        }
        throw new IOException("Can't find manifest file in modpack provided");
}

    private static String uniqueProfileKey(String preferredKey) {
        String base = preferredKey == null || preferredKey.trim().isEmpty()
                ? "Imported Modpack" : preferredKey.trim();
        String candidate = base;
        int suffix = 2;
        while (LauncherProfiles.mainProfileJson.profiles.containsKey(candidate)) {
            candidate = base + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private static String newInstanceId() {
        String instanceId;
        do {
            instanceId = java.util.UUID.randomUUID().toString();
        } while (Tools.getInstanceManager().paths().getInstanceRoot(instanceId).exists());
        return instanceId;
    }

    private static void registerProfile(String profileKey, String instanceId, String name,
                                        ModLoader modLoader, String icon) throws IOException {
        net.kdt.pojavlaunch.instances.InstanceManager manager = Tools.getInstanceManager();
        MinecraftProfile profile = new MinecraftProfile();
        profile.instanceId = instanceId;
        profile.gameDir = manager.paths().gameDirectoryValue(instanceId);
        profile.name = name;
        profile.lastVersionId = modLoader.getVersionId();
        profile.icon = icon;
        profile.created = MinecraftProfile.currentTimestamp();
        manager.updateMetadata(instanceId, profileKey, name, profile.lastVersionId);
        LauncherProfiles.mainProfileJson.profiles.put(profileKey, profile);
        LauncherProfiles.write();

        net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF.edit()
                .putString(net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
        LauncherProfiles.load();
    }

    interface InstallFunction {
        ModLoader installModpack(File modpackFile, File instanceDestination) throws IOException;
    }
}
