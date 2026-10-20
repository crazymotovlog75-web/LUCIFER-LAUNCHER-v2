package net.kdt.pojavlaunch.instances;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure path mapping for isolated Minecraft instances. Shared assets and libraries are an
 * optional cache; version metadata, client jars, game data, and natives are always instance-local.
 */
public final class InstancePathResolver {
    private final File storageRoot;
    private final File legacyMinecraftRoot;
    private volatile boolean sharedAssetsAndLibraries;

    public InstancePathResolver(File storageRoot, boolean sharedAssetsAndLibraries) {
        if (storageRoot == null) throw new IllegalArgumentException("storageRoot must not be null");
        this.storageRoot = storageRoot.getAbsoluteFile();
        this.legacyMinecraftRoot = new File(this.storageRoot, ".minecraft");
        this.sharedAssetsAndLibraries = sharedAssetsAndLibraries;
    }

    public File getStorageRoot() { return storageRoot; }
    public File getLegacyMinecraftRoot() { return legacyMinecraftRoot; }
    public File getLegacyVersionsRoot() { return new File(legacyMinecraftRoot, "versions"); }
    public File getInstancesRoot() { return new File(storageRoot, "instances"); }

    public void setSharedAssetsAndLibraries(boolean shared) {
        sharedAssetsAndLibraries = shared;
    }

    public boolean isSharedAssetsAndLibraries() {
        return sharedAssetsAndLibraries;
    }

    /** Stable, path-safe instance ID for launcher profile keys, including legacy human-readable keys. */
    public static String instanceIdForProfileKey(String profileKey) {
        if (profileKey == null || profileKey.trim().isEmpty()) {
            throw new IllegalArgumentException("profileKey must not be empty");
        }
        String trimmed = profileKey.trim();
        if (trimmed.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}")) {
            return trimmed.toLowerCase(java.util.Locale.ROOT);
        }
        return UUID.nameUUIDFromBytes(("lucifer-instance:" + trimmed).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String gameDirectoryValue(String instanceId) {
        return "./instances/" + safeSegment(instanceId);
    }

    public File getInstanceRoot(String instanceId) {
        return new File(getInstancesRoot(), safeSegment(instanceId));
    }

    /** The instance root itself is Minecraft's effective gameDir. */
    public File getGameDirectory(String instanceId) {
        return getInstanceRoot(instanceId);
    }

    public File getVersionsRoot(String instanceId) {
        return new File(getInstanceRoot(instanceId), "versions");
    }

    public File getVersionDirectory(String instanceId, String versionId) {
        return new File(getVersionsRoot(instanceId), safeSegment(versionId));
    }

    public File getVersionJson(String instanceId, String versionId) {
        return new File(getVersionDirectory(instanceId, versionId), safeSegment(versionId) + ".json");
    }

    public File getVersionJar(String instanceId, String versionId) {
        return new File(getVersionDirectory(instanceId, versionId), safeSegment(versionId) + ".jar");
    }

    public File getLibrariesRoot(String instanceId) {
        return sharedAssetsAndLibraries
                ? new File(legacyMinecraftRoot, "libraries")
                : new File(getInstanceRoot(instanceId), "libraries");
    }

    public File getAssetsRoot(String instanceId) {
        return sharedAssetsAndLibraries
                ? new File(legacyMinecraftRoot, "assets")
                : new File(getInstanceRoot(instanceId), "assets");
    }

    public File getNativesRoot(String instanceId, String versionId) {
        return new File(new File(getInstanceRoot(instanceId), "natives"), safeSegment(versionId));
    }

    public File getCrashReportsRoot(String instanceId) {
        return new File(getInstanceRoot(instanceId), "crash-reports");
    }

    public File resolveLegacyGameDirectory(String configuredGameDir) {
        if (configuredGameDir == null || configuredGameDir.trim().isEmpty()) return legacyMinecraftRoot;
        String path = configuredGameDir.trim();
        if (path.startsWith("amethyst://")) {
            path = path.substring("amethyst://".length());
            return new File(storageRoot, path);
        }
        File candidate = new File(path);
        return candidate.isAbsolute() ? candidate : new File(storageRoot, path);
    }

    /** Token values shared by vanilla and loader launch arguments. */
    public Map<String, String> getLaunchArgumentVariables(String instanceId, String versionId) {
        LinkedHashMap<String, String> variables = new LinkedHashMap<>();
        variables.put("game_directory", getGameDirectory(instanceId).getAbsolutePath());
        variables.put("assets_root", getAssetsRoot(instanceId).getAbsolutePath());
        variables.put("library_directory", getLibrariesRoot(instanceId).getAbsolutePath());
        variables.put("natives_directory", getNativesRoot(instanceId, versionId).getAbsolutePath());
        variables.put("client_jar", getVersionJar(instanceId, versionId).getAbsolutePath());
        variables.put("version_json", getVersionJson(instanceId, versionId).getAbsolutePath());
        variables.put("version_name", safeSegment(versionId));
        return variables;
    }

    private static String safeSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("path segment must not be empty");
        }
        String segment = value.trim();
        if (segment.equals(".") || segment.equals("..") || segment.contains("/") || segment.contains("\\") ||
                !segment.matches("[A-Za-z0-9._+-]{1,128}")) {
            throw new IllegalArgumentException("Unsafe path segment: " + value);
        }
        return segment;
    }
}
