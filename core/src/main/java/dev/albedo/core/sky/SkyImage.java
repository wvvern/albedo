package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A baked custom sky: an equirectangular ARGB image of the full sphere from {@link #TOP} degrees of elevation down to
 * {@link #BOTTOM}, with north at u = 0 and east to the right.
 */
public final class SkyImage {
    /** The elevation of the first row, in degrees. */
    public static final float TOP = 90f;
    /** The elevation of the last row, in degrees. */
    public static final float BOTTOM = -90f;
    /** The number of azimuth sectors of the horizon colors. */
    public static final int RING = 64;
    private static final AtomicLong SERIALS = new AtomicLong();

    private final int[] argb;
    private final int width, height;
    private final SkyDesign design;
    /** The mean linear {r, g, b} of each sector from 0 to 6 degrees of elevation, from north toward east. */
    private final float[] ring;
    private final long serial = SERIALS.incrementAndGet();

    SkyImage(int[] argb, int width, int height, SkyDesign design, float[] ring) {
        this.argb = argb;
        this.width = width;
        this.height = height;
        this.design = design;
        this.ring = ring;
    }

    /** The pixels, row by row from the top, which the caller must not change. */
    public int[] argb() { return argb; }

    public int width() { return width; }

    public int height() { return height; }

    /** The design this image shows. */
    public SkyDesign design() { return design; }

    /** A number that is different for each image, so that an adapter uploads an image only once. */
    public long serial() { return serial; }

    /** The texture coordinate v of an elevation in degrees, from 0 at the top row to 1 at the last row. */
    public static float v(double elevation) {
        return (float) Math.max(0, Math.min(1, (TOP - elevation) / (TOP - BOTTOM)));
    }

    /**
     * The sRGB fog color {r, g, b} from 0 to 1 near the horizon in the direction the camera looks.
     *
     * @param cameraYaw the Minecraft yaw in degrees, where 0 looks south and 90 looks west
     * @param brightness the factor of {@link SkyLight#brightness}, or 1
     */
    public float[] fogRgb(float cameraYaw, float brightness) {
        float[] out = new float[3];
        ringAt(ring, cameraYaw + 180, out);
        for (int c = 0; c < 3; c++) out[c] = Oklab.toSrgb(out[c] * brightness) / 255f;
        return out;
    }

    /**
     * The linear horizon color at an azimuth, mixed between the two nearest sector centers.
     *
     * @param ring the linear {r, g, b} of each sector
     * @param azimuth the angle in degrees from north toward east, in any range
     */
    static void ringAt(float[] ring, double azimuth, float[] out) {
        double at = ((azimuth % 360 + 360) % 360) / 360 * RING - 0.5;
        int base = (int) Math.floor(at);
        int i0 = Math.floorMod(base, RING), i1 = Math.floorMod(base + 1, RING);
        float f = (float) (at - base);
        for (int c = 0; c < 3; c++) out[c] = ring[i0 * 3 + c] * (1 - f) + ring[i1 * 3 + c] * f;
    }

    /**
     * A sphere of triangles around the camera with texture coordinates into this image, as x, y, z, u and v for each vertex.
     *
     * @param radius the distance of the vertices from the camera
     */
    public static float[] sphere(float radius) {
        int columns = 64, bands = 56;
        // Rows of equal height, so that v is linear in elevation across each triangle.
        float[] elevations = new float[bands + 1];
        for (int i = 0; i <= bands; i++) elevations[i] = TOP - (TOP - BOTTOM) * i / bands;
        float[] out = new float[(elevations.length - 1) * columns * 6 * 5];
        int k = 0;
        for (int r = 0; r < elevations.length - 1; r++) {
            for (int c = 0; c < columns; c++) {
                float u0 = c / (float) columns, u1 = (c + 1) / (float) columns;
                float e0 = elevations[r], e1 = elevations[r + 1];
                k = vertex(out, k, radius, u0, e0);
                k = vertex(out, k, radius, u0, e1);
                k = vertex(out, k, radius, u1, e1);
                k = vertex(out, k, radius, u0, e0);
                k = vertex(out, k, radius, u1, e1);
                k = vertex(out, k, radius, u1, e0);
            }
        }
        return out;
    }

    private static int vertex(float[] out, int k, float radius, float u, float elevation) {
        double lon = u * 2 * Math.PI, el = Math.toRadians(elevation);
        out[k] = (float) (Math.cos(el) * Math.sin(lon) * radius);
        out[k + 1] = (float) (Math.sin(el) * radius);
        out[k + 2] = (float) (-Math.cos(el) * Math.cos(lon) * radius);
        out[k + 3] = u;
        out[k + 4] = v(elevation);
        return k + 5;
    }
}
