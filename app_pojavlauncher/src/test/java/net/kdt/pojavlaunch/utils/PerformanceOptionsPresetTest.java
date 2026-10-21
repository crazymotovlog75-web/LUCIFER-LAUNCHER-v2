package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class PerformanceOptionsPresetTest {
    @Test
    public void presetCanRestoreValuesAndRemoveOptionsThatWereOriginallyAbsent() {
        Map<String, String> options = new HashMap<>();
        options.put("renderDistance", "12");
        options.put("graphicsMode", "fabulous");
        options.put("enableVsync", "true");
        options.put("mipmapLevels", "4");
        Map<String, String> original = new HashMap<>(options);

        PerformanceOptionsPreset.Snapshot snapshot = PerformanceOptionsPreset.capture(options);
        PerformanceOptionsPreset.apply(options);

        assertEquals("4", options.get("renderDistance"));
        assertEquals("4", options.get("simulationDistance"));
        assertEquals("false", options.get("enableVsync"));
        assertEquals("150", options.get("maxFps"));
        assertEquals("0", options.get("graphicsMode"));
        assertTrue(PerformanceOptionsPreset.isValidSnapshot(snapshot));

        PerformanceOptionsPreset.restore(options, snapshot);
        assertEquals(original, options);
    }

    @Test
    public void restoreKeepsOptionsChangedByTheUserWhileThePresetWasEnabled() {
        Map<String, String> options = new HashMap<>();
        options.put("renderDistance", "12");
        options.put("graphicsMode", "fabulous");

        PerformanceOptionsPreset.Snapshot snapshot = PerformanceOptionsPreset.capture(options);
        PerformanceOptionsPreset.apply(options);
        options.put("renderDistance", "8");
        options.put("maxFps", "90");

        PerformanceOptionsPreset.restore(options, snapshot);

        assertEquals("8", options.get("renderDistance"));
        assertEquals("90", options.get("maxFps"));
        assertEquals("fabulous", options.get("graphicsMode"));
        assertFalse(options.containsKey("simulationDistance"));
    }
}
