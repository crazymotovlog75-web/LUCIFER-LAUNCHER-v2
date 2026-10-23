package net.kdt.pojavlaunch.utils;

/** Resolution cap used by the opt-in experimental high-FPS profile. */
public final class HighFpsRenderScale {
    /** 30% in each dimension means about 9% as many rendered pixels at the cap. */
    public static final float MAX_SCALE = 0.30f;

    private HighFpsRenderScale() { }

    public static float resolve(float savedScale, boolean highFpsEnabled) {
        if (!highFpsEnabled) return savedScale;
        if (Float.isNaN(savedScale) || Float.isInfinite(savedScale) || savedScale <= 0f) {
            return MAX_SCALE;
        }
        return Math.min(savedScale, MAX_SCALE);
    }

    public static float pixelWorkloadFraction(float scale) {
        return scale * scale;
    }
}
