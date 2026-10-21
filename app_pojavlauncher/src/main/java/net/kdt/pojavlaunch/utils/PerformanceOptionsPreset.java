package net.kdt.pojavlaunch.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reversible, per-instance Minecraft video settings for the opt-in high-FPS profile. */
public final class PerformanceOptionsPreset {
    private static final Map<String, String> PRESET;

    static {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        options.put("renderDistance", "4");
        options.put("simulationDistance", "4");
        options.put("enableVsync", "false");
        options.put("maxFps", "150");
        options.put("graphicsMode", "0");
        options.put("fancyGraphics", "false");
        options.put("clouds", "false");
        options.put("renderClouds", "false");
        options.put("entityShadows", "false");
        options.put("entityDistanceScaling", "0.5");
        options.put("mipmapLevels", "0");
        options.put("particles", "2");
        options.put("biomeBlendRadius", "0");
        // 0 is the off value in older versions and is also parsed as false by newer ones.
        options.put("ao", "0");
        options.put("fovEffectScale", "0.0");
        options.put("distortionEffectScale", "0.0");
        PRESET = Collections.unmodifiableMap(options);
    }

    private PerformanceOptionsPreset() { }

    /** Stores the original values, including which options were absent before the preset. */
    public static final class Snapshot {
        public Map<String, String> originalValues = new LinkedHashMap<>();
        public Set<String> originallyMissing = new LinkedHashSet<>();
    }

    public static Snapshot capture(Map<String, String> currentOptions) {
        Snapshot snapshot = new Snapshot();
        for (String key : PRESET.keySet()) {
            if (currentOptions.containsKey(key)) {
                snapshot.originalValues.put(key, currentOptions.get(key));
            } else {
                snapshot.originallyMissing.add(key);
            }
        }
        return snapshot;
    }

    /** Applies conservative graphics settings without changing RAM or renderer selection. */
    public static void apply(Map<String, String> currentOptions) {
        currentOptions.putAll(PRESET);
    }

    /**
     * Restores an option only if it still has the value written by this preset. If a user changed
     * an option while the mode was enabled, that edit is kept instead of being overwritten.
     */
    public static void restore(Map<String, String> currentOptions, Snapshot snapshot) {
        if (!isValidSnapshot(snapshot)) return;
        for (Map.Entry<String, String> entry : PRESET.entrySet()) {
            String key = entry.getKey();
            if (!Objects.equals(currentOptions.get(key), entry.getValue())) continue;
            if (snapshot.originallyMissing.contains(key)) {
                currentOptions.remove(key);
            } else {
                currentOptions.put(key, snapshot.originalValues.get(key));
            }
        }
    }

    public static boolean isValidSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.originalValues == null || snapshot.originallyMissing == null) {
            return false;
        }
        for (String key : PRESET.keySet()) {
            boolean hasValue = snapshot.originalValues.containsKey(key);
            boolean wasMissing = snapshot.originallyMissing.contains(key);
            if (hasValue == wasMissing) return false;
            if (hasValue && snapshot.originalValues.get(key) == null) return false;
        }
        return true;
    }
}
