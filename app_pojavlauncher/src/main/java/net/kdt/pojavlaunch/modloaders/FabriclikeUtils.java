package net.kdt.pojavlaunch.modloaders;

import android.util.Log;

import com.google.gson.JsonSyntaxException;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Metadata and profile access for Fabric and Quilt's official APIs. */
public class FabriclikeUtils {
    public static final FabriclikeUtils FABRIC_UTILS = new FabriclikeUtils(
            "https://meta.fabricmc.net/v2", "fabric", "Fabric", "fabric");
    public static final FabriclikeUtils QUILT_UTILS = new FabriclikeUtils(
            "https://meta.quiltmc.org/v3", "quilt", "Quilt", "quilt");

    private static final String TAG = "FabricInstall";
    private static final String LOADER_METADATA_URL = "%s/versions/loader/%s";
    private static final String GAME_METADATA_URL = "%s/versions/game";
    private static final String JSON_DOWNLOAD_URL = "%s/versions/loader/%s/%s/profile/json";
    private static final int MAX_PROFILE_JSON_CHARS = 2 * 1024 * 1024;

    private final String mApiUrl;
    private final String mCachePrefix;
    private final String mName;
    private final String mIconName;

    private FabriclikeUtils(String apiUrl, String cachePrefix, String name, String iconName) {
        mApiUrl = apiUrl;
        mCachePrefix = cachePrefix;
        mName = name;
        mIconName = iconName;
    }

    public FabricVersion[] downloadGameVersions() throws IOException {
        final FabricVersion[] versions;
        try {
            versions = DownloadUtils.downloadStringCached(
                    String.format(GAME_METADATA_URL, mApiUrl),
                    mCachePrefix + "_game_versions",
                    FabriclikeUtils::deserializeRawVersions);
        } catch (DownloadUtils.ParseException e) {
            throw new IOException("The " + mName + " game-version metadata was invalid", e);
        }
        FabricVersion[] valid = filterValidVersions(versions);
        if (valid.length == 0) throw new IOException("The " + mName + " API returned no valid game versions");
        return valid;
    }

    /** Returns only loader releases reported for the supplied Minecraft version. */
    public FabricVersion[] downloadLoaderVersions(String gameVersion) throws IOException {
        requireVersionToken(gameVersion, "Minecraft version");
        String encodedGameVersion = encodePathSegment(gameVersion);
        String url = String.format(LOADER_METADATA_URL, mApiUrl, encodedGameVersion);
        Log.d(TAG, mName + " loader metadata for Minecraft " + gameVersion);

        final FabricVersion[] versions;
        try {
            versions = DownloadUtils.downloadStringCached(
                    url,
                    mCachePrefix + "_loader_versions." + encodedGameVersion,
                    FabriclikeUtils::deserializeLoaderVersions);
        } catch (DownloadUtils.ParseException e) {
            throw new IOException("The " + mName + " loader metadata was invalid for Minecraft " + gameVersion, e);
        }
        // Intentionally do not fall back to the global loader list: it contains versions that may
        // not support this game release.
        return filterValidVersions(versions);
    }

    /** Fetches and validates the installer profile after rechecking the live game/loader metadata. */
    public String downloadProfileJson(String gameVersion, String loaderVersion) throws IOException {
        requireVersionToken(gameVersion, "Minecraft version");
        requireVersionToken(loaderVersion, mName + " version");

        boolean gameSupported = false;
        for (FabricVersion game : downloadGameVersionsFresh()) {
            if (gameVersion.equals(game.version)) {
                gameSupported = true;
                break;
            }
        }
        if (!gameSupported) {
            throw new IOException("Minecraft " + gameVersion + " is no longer listed by the " + mName + " API");
        }

        boolean loaderSupported = false;
        for (FabricVersion loader : downloadLoaderVersionsFresh(gameVersion)) {
            if (loaderVersion.equals(loader.version)) {
                loaderSupported = true;
                break;
            }
        }
        if (!loaderSupported) {
            throw new IOException(mName + " Loader " + loaderVersion +
                    " is not listed for Minecraft " + gameVersion);
        }

        String profileUrl = String.format(JSON_DOWNLOAD_URL, mApiUrl,
                encodePathSegment(gameVersion), encodePathSegment(loaderVersion));
        String profileJson = DownloadUtils.downloadStringWithRetries(profileUrl);
        validateProfileJson(profileJson, gameVersion, loaderVersion);
        return profileJson;
    }

    private FabricVersion[] downloadGameVersionsFresh() throws IOException {
        try {
            FabricVersion[] versions = deserializeRawVersions(DownloadUtils.downloadStringWithRetries(
                    String.format(GAME_METADATA_URL, mApiUrl)));
            FabricVersion[] valid = filterValidVersions(versions);
            if (valid.length == 0) throw new IOException("The " + mName + " API returned no valid game versions");
            return valid;
        } catch (DownloadUtils.ParseException e) {
            throw new IOException("The " + mName + " game-version metadata was invalid", e);
        }
    }

    private FabricVersion[] downloadLoaderVersionsFresh(String gameVersion) throws IOException {
        String url = String.format(LOADER_METADATA_URL, mApiUrl, encodePathSegment(gameVersion));
        try {
            return filterValidVersions(deserializeLoaderVersions(DownloadUtils.downloadStringWithRetries(url)));
        } catch (DownloadUtils.ParseException e) {
            throw new IOException("The " + mName + " loader metadata was invalid for Minecraft " + gameVersion, e);
        }
    }

    private void validateProfileJson(String json, String gameVersion, String loaderVersion) throws IOException {
        if (json == null || json.isEmpty() || json.length() > MAX_PROFILE_JSON_CHARS) {
            throw new IOException("The " + mName + " profile response was empty or too large");
        }
        try {
            JSONObject profile = new JSONObject(json);
            String id = profile.optString("id", "").trim();
            String parentVersion = profile.optString("inheritsFrom", "").trim();
            JSONArray libraries = profile.optJSONArray("libraries");
            if (!isValidVersionToken(id) || !gameVersion.equals(parentVersion) ||
                    libraries == null || libraries.length() == 0) {
                throw new IOException("The " + mName + " profile did not match the selected game and loader versions");
            }
            for (int i = 0; i < libraries.length(); i++) {
                JSONObject library = libraries.optJSONObject(i);
                if (library == null || library.optString("name", "").trim().isEmpty()) {
                    throw new IOException("The " + mName + " profile contains an invalid library entry");
                }
            }
        } catch (JSONException e) {
            throw new IOException("The " + mName + " profile was not valid JSON", e);
        }
    }

    public String createJsonDownloadUrl(String gameVersion, String loaderVersion) {
        requireVersionTokenUnchecked(gameVersion, "Minecraft version");
        requireVersionTokenUnchecked(loaderVersion, mName + " version");
        return String.format(JSON_DOWNLOAD_URL, mApiUrl,
                encodePathSegment(gameVersion), encodePathSegment(loaderVersion));
    }

    private static String encodePathSegment(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossibleOnAndroid) {
            throw new IllegalStateException("UTF-8 is not supported", impossibleOnAndroid);
        }
    }

    private static void requireVersionToken(String value, String description) throws IOException {
        if (!isValidVersionToken(value)) throw new IOException("Invalid " + description);
    }

    private static void requireVersionTokenUnchecked(String value, String description) {
        if (!isValidVersionToken(value)) throw new IllegalArgumentException("Invalid " + description);
    }

    private static boolean isValidVersionToken(String value) {
        return value != null && value.length() > 0 && value.length() <= 64 &&
                value.matches("[A-Za-z0-9][A-Za-z0-9._+-]*");
    }

    private static FabricVersion[] filterValidVersions(FabricVersion[] versions) {
        if (versions == null || versions.length == 0) return new FabricVersion[0];
        List<FabricVersion> valid = new ArrayList<>(versions.length);
        Set<String> seen = new HashSet<>();
        for (FabricVersion version : versions) {
            if (version == null || !isValidVersionToken(version.version) || !seen.add(version.version)) continue;
            valid.add(version);
        }
        return valid.toArray(new FabricVersion[0]);
    }

    public String getName() { return mName; }
    public String getIconName() { return mIconName; }

    private static FabricVersion[] deserializeLoaderVersions(String input) throws DownloadUtils.ParseException {
        try {
            JSONArray jsonArray = new JSONArray(input);
            List<FabricVersion> versions = new ArrayList<>(jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject entry = jsonArray.optJSONObject(i);
                if (entry == null) continue;
                JSONObject loader = entry.optJSONObject("loader");
                JSONObject source = loader != null ? loader : entry;
                String version = source.optString("version", "").trim();
                if (!isValidVersionToken(version)) continue;
                FabricVersion fabricVersion = new FabricVersion();
                fabricVersion.version = version;
                fabricVersion.stable = source.has("stable")
                        ? source.optBoolean("stable", false)
                        : !version.toLowerCase(java.util.Locale.ROOT).contains("beta");
                versions.add(fabricVersion);
            }
            return versions.toArray(new FabricVersion[0]);
        } catch (JSONException e) {
            throw new DownloadUtils.ParseException(e);
        }
    }

    private static FabricVersion[] deserializeRawVersions(String input) throws DownloadUtils.ParseException {
        try {
            FabricVersion[] versions = Tools.GLOBAL_GSON.fromJson(input, FabricVersion[].class);
            if (versions == null) throw new JsonSyntaxException("Metadata was not a JSON array");
            return versions;
        } catch (JsonSyntaxException e) {
            throw new DownloadUtils.ParseException(e);
        }
    }
}
