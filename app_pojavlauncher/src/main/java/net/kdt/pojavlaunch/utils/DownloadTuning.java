package net.kdt.pojavlaunch.utils;

/** Bounded, user-configurable network parallelism for Minecraft asset downloads. */
public final class DownloadTuning {
    public static final String PREFERENCE_KEY = "downloadParallelism";
    public static final int DEFAULT_PARALLEL_DOWNLOADS = 8;
    public static final int MIN_PARALLEL_DOWNLOADS = 2;
    public static final int MAX_PARALLEL_DOWNLOADS = 16;

    private DownloadTuning() { }

    public static int normalizeParallelDownloads(int requested) {
        return Math.max(MIN_PARALLEL_DOWNLOADS, Math.min(MAX_PARALLEL_DOWNLOADS, requested));
    }

    /** ArrayBlockingQueue requires positive capacity even if there is no work to schedule. */
    public static int queueCapacity(int scheduledTaskCount) {
        return Math.max(1, scheduledTaskCount);
    }
}
