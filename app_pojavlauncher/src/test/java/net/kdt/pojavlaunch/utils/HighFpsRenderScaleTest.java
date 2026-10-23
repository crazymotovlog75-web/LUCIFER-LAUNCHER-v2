package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HighFpsRenderScaleTest {
    @Test
    public void highFpsProfileCapsScaleWithoutChangingTheSavedValue() {
        assertEquals(0.30f, HighFpsRenderScale.resolve(1f, true), 0f);
        assertEquals(0.30f, HighFpsRenderScale.resolve(0.5f, true), 0f);
        assertEquals(0.25f, HighFpsRenderScale.resolve(0.25f, true), 0f);
        assertEquals(0.75f, HighFpsRenderScale.resolve(0.75f, false), 0f);
    }

    @Test
    public void capRendersLessThanOneTenthOfTheOriginalPixels() {
        assertTrue(HighFpsRenderScale.pixelWorkloadFraction(HighFpsRenderScale.MAX_SCALE) <= 0.1f);
    }
}
