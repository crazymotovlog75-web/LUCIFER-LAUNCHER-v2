package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadTuningTest {
    @Test
    public void parallelismIsBoundedToUiRange() {
        assertEquals(DownloadTuning.MIN_PARALLEL_DOWNLOADS, DownloadTuning.normalizeParallelDownloads(0));
        assertEquals(8, DownloadTuning.normalizeParallelDownloads(8));
        assertEquals(DownloadTuning.MAX_PARALLEL_DOWNLOADS, DownloadTuning.normalizeParallelDownloads(99));
    }

    @Test
    public void emptyDownloadPlanStillHasAValidQueueCapacity() {
        assertEquals(1, DownloadTuning.queueCapacity(0));
        assertEquals(4, DownloadTuning.queueCapacity(4));
    }
}
