package net.kdt.pojavlaunch.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats launcher profile IDs into readable Minecraft and mod-loader version lines. */
public final class ProfileVersionDisplay {
    private static final Pattern MINECRAFT_VERSION =
            Pattern.compile("(?<!\\d)(1\\.[0-9]+(?:\\.[0-9]+)?)(?!\\d)");
    private static final Pattern NEOFORGE_VERSION = Pattern.compile("^(\\d{2})\\.(\\d+)(?:\\.\\d+).*$");

    private ProfileVersionDisplay() { }

    /**
     * Returns complete, human-readable version lines without depending on installed version files.
     * Examples: "Fabric - 1.21.10\nFabric Loader - 0.17.3" and "Minecraft - 1.21.10".
     */
    public static String format(String versionId, String profileName) {
        if (versionId == null || versionId.trim().isEmpty()) {
            return profileName == null || profileName.trim().isEmpty()
                    ? "Unknown version" : profileName.trim();
        }

        String rawId = versionId.trim();
        String loader = findLoader(rawId);
        String minecraftVersion = findMinecraftVersion(rawId);
        if (minecraftVersion.isEmpty()) minecraftVersion = findMinecraftVersion(profileName);

        String loaderVersion = loader == null ? "" : findLoaderVersion(rawId, loader, minecraftVersion);
        if ("NeoForge".equals(loader)) {
            if (loaderVersion.isEmpty()) loaderVersion = valueAfterPrefix(rawId, "neoforge-");
            if (minecraftVersion.isEmpty()) minecraftVersion = minecraftFromNeoForge(loaderVersion);
        }

        if (loader == null) {
            return minecraftVersion.isEmpty() ? rawId : "Minecraft - " + minecraftVersion;
        }

        String firstLine;
        if (minecraftVersion.isEmpty()) {
            firstLine = loader + " - " + rawId;
        } else if ("OptiFine".equals(loader) || "LiteLoader".equals(loader)) {
            firstLine = "Minecraft - " + minecraftVersion;
        } else {
            firstLine = loader + " - " + minecraftVersion;
        }

        if (loaderVersion.isEmpty()) return firstLine;
        if ("OptiFine".equals(loader)) return firstLine + "\nOptiFine - " + loaderVersion;
        return firstLine + "\n" + loader + " Loader - " + loaderVersion;
    }

    private static String findLoader(String versionId) {
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge")) return "NeoForge";
        if (lower.contains("fabric")) return "Fabric";
        if (lower.contains("quilt")) return "Quilt";
        if (lower.contains("forge")) return "Forge";
        if (lower.contains("optifine")) return "OptiFine";
        if (lower.contains("liteloader")) return "LiteLoader";
        return null;
    }

    private static String findMinecraftVersion(String text) {
        if (text == null) return "";
        Matcher matcher = MINECRAFT_VERSION.matcher(text);
        String lastMatch = "";
        while (matcher.find()) lastMatch = matcher.group(1);
        return lastMatch;
    }

    private static String findLoaderVersion(String versionId, String loader, String minecraftVersion) {
        String lowerId = versionId.toLowerCase(Locale.ROOT);
        String lowerLoader = loader.toLowerCase(Locale.ROOT);

        // Fabric and Quilt IDs put the loader version before the Minecraft version.
        if ("Fabric".equals(loader) || "Quilt".equals(loader)) {
            String prefix = lowerLoader + "-loader-";
            int prefixIndex = lowerId.indexOf(prefix);
            if (prefixIndex >= 0) {
                String remainder = versionId.substring(prefixIndex + prefix.length());
                if (!minecraftVersion.isEmpty()) {
                    int mcSuffixIndex = remainder.toLowerCase(Locale.ROOT)
                            .lastIndexOf("-" + minecraftVersion.toLowerCase(Locale.ROOT));
                    if (mcSuffixIndex >= 0) return trimSeparators(remainder.substring(0, mcSuffixIndex));
                }
                return trimSeparators(remainder);
            }
        }

        // Forge, NeoForge and OptiFine commonly append their own version after the MC version.
        if (!minecraftVersion.isEmpty()) {
            int mcIndex = lowerId.lastIndexOf(minecraftVersion.toLowerCase(Locale.ROOT));
            if (mcIndex >= 0) {
                String suffix = trimSeparators(versionId.substring(mcIndex + minecraftVersion.length()));
                String prefix = lowerLoader + "-";
                if (suffix.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suffix = suffix.substring(prefix.length());
                }
                if ("OptiFine".equals(loader) && suffix.toLowerCase(Locale.ROOT).startsWith("optifine")) {
                    suffix = trimSeparators(suffix.substring("optifine".length()));
                }
                if ("LiteLoader".equals(loader) && suffix.toLowerCase(Locale.ROOT).startsWith("liteloader")) {
                    suffix = trimSeparators(suffix.substring("liteloader".length()));
                }
                return suffix;
            }
        }

        String prefix = lowerLoader + "-";
        if (lowerId.startsWith(prefix)) {
            return trimSeparators(versionId.substring(prefix.length()));
        }
        return "";
    }

    private static String valueAfterPrefix(String value, String prefix) {
        if (value == null || prefix == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(prefix.toLowerCase(Locale.ROOT));
        if (index < 0) return "";
        return trimSeparators(value.substring(index + prefix.length()));
    }

    private static String minecraftFromNeoForge(String neoForgeVersion) {
        if (neoForgeVersion == null || neoForgeVersion.isEmpty()) return "";
        Matcher matcher = NEOFORGE_VERSION.matcher(neoForgeVersion);
        if (!matcher.matches()) return "";
        String major = matcher.group(1);
        String minor = matcher.group(2);
        return "0".equals(minor) ? "1." + major : "1." + major + "." + minor;
    }

    private static String trimSeparators(String value) {
        if (value == null) return "";
        int start = 0;
        int end = value.length();
        while (start < end && isSeparator(value.charAt(start))) start++;
        while (end > start && isSeparator(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean isSeparator(char character) {
        return character == '-' || character == '_' || character == '.' || character == ' ';
    }
}
