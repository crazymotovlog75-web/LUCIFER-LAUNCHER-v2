package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProfileVersionDisplayTest {
    @Test
    public void fabricDisplaysMinecraftAndLoaderVersionsOnSeparateLines() {
        assertEquals("Fabric - 1.21.10\nFabric Loader - 0.17.3",
                ProfileVersionDisplay.format("fabric-loader-0.17.3-1.21.10", "Minecraft 1.21.10"));
    }

    @Test
    public void forgeSupportsBothCommonVersionIdOrders() {
        assertEquals("Forge - 1.21.10\nForge Loader - 57.0.0",
                ProfileVersionDisplay.format("forge-1.21.10-57.0.0", null));
        assertEquals("Forge - 1.21.10\nForge Loader - 57.0.0",
                ProfileVersionDisplay.format("1.21.10-forge-57.0.0", null));
    }

    @Test
    public void neoforgeDerivesMinecraftVersionWhenTheIdOnlyContainsItsLoaderVersion() {
        assertEquals("NeoForge - 1.21.10\nNeoForge Loader - 21.10.47",
                ProfileVersionDisplay.format("neoforge-21.10.47", null));
    }

    @Test
    public void vanillaAndOptifineRemainReadable() {
        assertEquals("Minecraft - 1.21.10",
                ProfileVersionDisplay.format("1.21.10", "Minecraft 1.21.10"));
        assertEquals("Minecraft - 1.20.1\nOptiFine - HD_U_I6",
                ProfileVersionDisplay.format("OptiFine_1.20.1_HD_U_I6", null));
    }

    @Test
    public void unknownCustomIdsAreShownInFull() {
        String customId = "custom-client-build-long-name-2026";
        assertEquals(customId, ProfileVersionDisplay.format(customId, null));
    }
}
