package dev.albedo.core.dev;

import dev.albedo.core.sky.Oklab;

import java.util.Arrays;

/** The image, timing and view math of the harness checks. */
final class HarnessImages {
    /** A pixel is far off when a channel differs by more 8-bit steps than this, which dither and rounding never do. */
    static final int FAR_STEP = 48;

    private HarnessImages() {}

    /** The middle part of a frame, a fifth of its width and of its height, where a check puts the thing that it looks at. */
    static int[] middle(int[] argb, int width, int height) {
        int w = Math.max(1, width / 5), h = Math.max(1, height / 5), x0 = (width - w) / 2, y0 = (height - h) / 2;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(argb, (y0 + y) * width + x0, out, y * w, w);
        return out;
    }

    /**
     * The mean difference of the color channels of two frames of the same size in 8-bit steps, and the part of the pixels
     * that have a channel more than {@code farStep} steps off.
     *
     * @return {mean, far-off part}, or {Infinity, 1} when the frames have different sizes
     */
    static double[] frameDifference(int[] a, int[] b, int farStep) {
        if (a.length != b.length || a.length == 0) return new double[]{Double.POSITIVE_INFINITY, 1};
        long sum = 0;
        int far = 0;
        for (int i = 0; i < a.length; i++) {
            int largest = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                int d = Math.abs(((a[i] >> shift) & 0xFF) - ((b[i] >> shift) & 0xFF));
                sum += d;
                largest = Math.max(largest, d);
            }
            if (largest > farStep) far++;
        }
        return new double[]{sum / (3.0 * a.length), far / (double) a.length};
    }

    /**
     * The mean, the 99th percentile and the largest OKLab distance between the pixels of two images of the same size.
     *
     * @return {mean, 99th percentile by {@link #floorRank}, largest}
     */
    static double[] oklabDifference(int[] a, int[] b) {
        if (a.length != b.length || a.length == 0) throw new IllegalArgumentException("the images have different sizes");
        double[] d = new double[a.length];
        float[] la = new float[3], lb = new float[3];
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            lab(a[i], la);
            lab(b[i], lb);
            d[i] = Math.sqrt((la[0] - lb[0]) * (la[0] - lb[0]) + (la[1] - lb[1]) * (la[1] - lb[1]) + (la[2] - lb[2]) * (la[2] - lb[2]));
            sum += d[i];
        }
        Arrays.sort(d);
        return new double[]{sum / d.length, d[floorRank(d.length, 0.99)], d[d.length - 1]};
    }

    private static void lab(int argb, float[] out) {
        Oklab.fromLinear(Oklab.toLinear(argb >> 16), Oklab.toLinear(argb >> 8), Oklab.toLinear(argb), out);
    }

    /** The {@code p}th percentile of nanosecond times by {@link #nearestRank}, in milliseconds. */
    static double percentileMs(long[] nanos, int p) {
        if (nanos.length == 0) return Double.NaN;
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return sorted[nearestRank(sorted.length, p)] / 1e6;
    }

    /** The index of the {@code p}th percentile of {@code n} sorted values by the nearest rank: ceil(p / 100 * n) - 1. */
    static int nearestRank(int n, int p) {
        return Math.min(n - 1, Math.max(0, (int) Math.ceil(p / 100.0 * n) - 1));
    }

    /** The index of a fraction of {@code n} sorted values, rounded down: floor(fraction * n), at most n - 1. */
    static int floorRank(int n, double fraction) {
        return Math.min(n - 1, (int) (n * fraction));
    }

    /**
     * The sky directions of the corners of a camera view, top left, bottom left, bottom right and top right, as x, y and z
     * each, on the plane one unit in front of the camera.
     *
     * @param yaw the Minecraft yaw in degrees, where 0 looks south and 90 looks west
     * @param pitch the Minecraft pitch in degrees, where a negative pitch looks up
     * @param fov the vertical field of view in degrees
     * @param aspect the width over the height
     */
    static float[] viewCorners(float yaw, float pitch, float fov, float aspect) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] forward = {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
        double[] right = {-Math.cos(y), 0, -Math.sin(y)};
        double[] up = {right[1] * forward[2] - right[2] * forward[1], right[2] * forward[0] - right[0] * forward[2],
            right[0] * forward[1] - right[1] * forward[0]};
        double ty = Math.tan(Math.toRadians(fov) / 2), tx = ty * aspect;
        double[][] corners = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
        float[] out = new float[12];
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k < 3; k++) out[c * 3 + k] = (float) (forward[k] + corners[c][0] * tx * right[k] + corners[c][1] * ty * up[k]);
        }
        return out;
    }
}
