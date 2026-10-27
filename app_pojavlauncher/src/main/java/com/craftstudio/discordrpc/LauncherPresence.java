package com.craftstudio.discordrpc;

/**
 * Launcher-specific presence presets. This is the layout the Discord card in
 * the reference design uses:
 *
 * <pre>
 *   LUCIFER LAUNCHER                                   (application name)
 *   Playing Minecraft fabric-loader-0.17.3-1.21.10     (details)
 *   Fabric 1.21.10                                     (state)
 *   0:41                                               (timestamps.start)
 * </pre>
 */
public final class LauncherPresence {

    /** Asset key uploaded to the Discord Developer Portal. */
    private static volatile String largeImageKey = "lucifer_logo";
    private static volatile String largeImageText = "LUCIFER LAUNCHER";

    private LauncherPresence() {
    }

    /** Override the art asset key if your Developer Portal uses another name. */
    public static void setLargeImage(String key, String tooltip) {
        largeImageKey = key;
        largeImageText = tooltip;
    }

    /** Launcher is open, no game running. Timer counts since the launcher opened. */
    public static RichPresence idle() {
        return RichPresence.builder()
                .details("In the launcher")
                .state("Browsing versions")
                .startNow()
                .largeImage(largeImageKey, largeImageText)
                .build();
    }

    /** Launcher is open but the bridge/menu is idle without a timer. */
    public static RichPresence idleWithoutTimer() {
        return RichPresence.builder()
                .details("In the launcher")
                .state("Browsing versions")
                .largeImage(largeImageKey, largeImageText)
                .build();
    }

    /**
     * @param mcVersion     e.g. "1.21.10"
     * @param modLoader     e.g. "Fabric", "Forge", "Vanilla", "NeoForge"
     * @param profileName   the version/instance id, e.g.
     *                      "fabric-loader-0.17.3-1.21.10"
     * @param startMillis   session start (elapsed timer)
     */
    public static RichPresence playing(String mcVersion, String modLoader, String profileName, long startMillis) {
        String loader = isBlank(modLoader) ? "Vanilla" : modLoader;
        String target = isBlank(profileName) ? mcVersion : profileName;
        String details = target == null || target.isEmpty()
                ? "Playing Minecraft"
                : "Playing Minecraft " + target;
        String state = mcVersion == null || mcVersion.isEmpty()
                ? loader
                : loader + " " + mcVersion;
        return RichPresence.builder()
                .details(details)
                .state(state)
                .startTimestamp(startMillis)
                .largeImage(largeImageKey, largeImageText)
                .build();
    }

    /** Guesses the loader name from a version id such as {@code fabric-loader-0.17.3-1.21.10}. */
    public static String detectLoader(String profileName) {
        if (isBlank(profileName)) return "Vanilla";
        String p = profileName.toLowerCase();
        if (p.contains("fabric")) return "Fabric";
        if (p.contains("quilt")) return "Quilt";
        if (p.contains("neoforge")) return "NeoForge";
        if (p.contains("forge")) return "Forge";
        if (p.contains("optifine")) return "OptiFine";
        return "Vanilla";
    }

    /**
     * Pulls the Minecraft version out of a profile id.
     * {@code fabric-loader-0.17.3-1.21.10} → {@code 1.21.10}.
     */
    public static String detectMinecraftVersion(String profileName) {
        if (isBlank(profileName)) return "";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)(?:[^\\d]|$)").matcher(profileName);
        String best = "";
        while (m.find()) {
            String candidate = m.group(1);
            // prefer the last version-like token (loader versions come first)
            if (candidate.split("\\.").length >= 2) best = candidate;
        }
        return best;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
