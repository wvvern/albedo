package dev.albedo.core.dev;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessImagesTest {
    @Test
    void theSkyCompareMeasuresTheOklabDistance() {
        int[] gray = {0xFF808080, 0xFF808080, 0xFF808080, 0xFF808080};
        assertEquals(0, HarnessImages.oklabDifference(gray, gray.clone())[0], 1e-9);
        int[] one = gray.clone();
        one[3] = 0xFFFFFFFF;
        double[] d = HarnessImages.oklabDifference(gray, one);
        assertTrue(d[2] > 0.3 && d[2] < 0.5, "white is far from gray: " + d[2]);
        assertEquals(d[2] / 4, d[0], 1e-9, "the mean of one far pixel in four");
        assertThrows(IllegalArgumentException.class, () -> HarnessImages.oklabDifference(gray, new int[2]));
    }

    @Test
    void theFrameDifferenceMeasuresTheMeanAndTheFarOffPixels() {
        int[] gray = new int[1000];
        Arrays.fill(gray, 0xFF808080);
        int[] dithered = gray.clone();
        for (int i = 0; i < dithered.length; i += 2) dithered[i] = 0xFF818080;
        double[] d = HarnessImages.frameDifference(gray, dithered, HarnessImages.FAR_STEP);
        assertEquals(1 / 6.0, d[0], 1e-9, "one step in one channel of half the pixels");
        assertEquals(0, d[1], 1e-9);
        int[] checker = gray.clone();
        for (int i = 0; i < checker.length; i += 2) checker[i] = i % 4 == 0 ? 0xFF000000 : 0xFFF0F0F0;
        double[] far = HarnessImages.frameDifference(gray, checker, HarnessImages.FAR_STEP);
        assertEquals(0.5, far[1], 1e-9, "each black or light texel of the checker is far off");
        assertEquals(1, HarnessImages.frameDifference(gray, new int[2], HarnessImages.FAR_STEP)[1], 1e-9, "frames of different sizes");
    }

    @Test
    void theMiddleIsAFifthOfTheFrame() {
        int[] frame = new int[10 * 5];
        Arrays.fill(frame, 0xFF000000);
        frame[2 * 10 + 4] = 0xFFFFFFFF;
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFF000000}, HarnessImages.middle(frame, 10, 5), "two by one pixels at the middle");
    }

    @Test
    void theTwoPercentileRulesKeepTheirIndices() {
        assertEquals(94, HarnessImages.nearestRank(100, 95), "ceil(95) - 1");
        assertEquals(0, HarnessImages.nearestRank(3, 0), "at least the first value");
        assertEquals(9, HarnessImages.nearestRank(10, 100), "the largest value");
        assertEquals(99, HarnessImages.floorRank(100, 0.99), "floor(99)");
        assertEquals(9, HarnessImages.floorRank(10, 1.0), "at most the last value");
        assertEquals(1, HarnessImages.floorRank(2, 0.99), "floor(1.98) is the second value");
        assertEquals(1, HarnessImages.nearestRank(2, 99), "ceil(1.98) - 1 is also the second value");
    }

    @Test
    void thePercentileOfTimesIsInMilliseconds() {
        long[] nanos = {4_000_000, 1_000_000, 3_000_000, 2_000_000};
        assertEquals(2.0, HarnessImages.percentileMs(nanos, 50), 1e-9);
        assertEquals(4.0, HarnessImages.percentileMs(nanos, 100), 1e-9);
        assertTrue(Double.isNaN(HarnessImages.percentileMs(new long[0], 50)));
    }

    @Test
    void theViewCornersOfSkyTimeLookWhereTheCameraLooks() {
        // Yaw 180 looks north (-z), and pitch -90 looks straight up.
        float[] north = HarnessImages.viewCorners(180, 0, 90, 2);
        assertEquals(0, (north[0] + north[3] + north[6] + north[9]) / 4, 1e-5, "the view is centered on the north");
        assertEquals(-1, (north[2] + north[5] + north[8] + north[11]) / 4, 1e-5, "the center is 1 to the north");
        assertEquals(1, north[1], 1e-5, "the top edge is tan(45) up");
        assertEquals(-2, north[0], 1e-5, "the left edge at aspect 2 is to the west, the left of a camera that looks north");
        float[] up = HarnessImages.viewCorners(0, -90, 70, 1);
        assertEquals(4, (up[1] + up[4] + up[7] + up[10]), 1e-5, "each corner of a view straight up is 1 up");
    }
}
