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
    /** The lowest elevation in degrees that the layers fill when the sky stops at the horizon, because filters near it read rows below it. */
    private static final float HORIZON_BOTTOM = -15f;
    /** The number of azimuth sectors of the horizon colors. */
    public static final int RING = 64;
    /** The cells across, and half as many down, of the colors that the fog of a turning sky reads. */
    private static final int CELLS = 64;
    private static final AtomicLong SERIALS = new AtomicLong();

    private final int[] argb;
    private final int width, height;
    private final SkyDesign design;
    /** The mean linear {r, g, b} of each sector from 0 to 6 degrees of elevation, from north toward east. */
    private final float[] ring;
    private final SkyProgram program;
    private final int termWidth, termHeight;
    /** The half float RGBA texels of the term images, one under the other in the order of their slots. */
    private final short[] terms;
    private final long serial = SERIALS.incrementAndGet();
    /** The mean linear {r, g, b} of cells of about 6 degrees on the whole sphere, made at the first fog of a turning sky. */
    private volatile float[] cells;

    SkyImage(int[] argb, int width, int height, SkyDesign design, float[] ring, SkyProgram program, int termWidth, int termHeight,
            short[] terms) {
        this.argb = argb;
        this.width = width;
        this.height = height;
        this.design = design;
        this.ring = ring;
        this.program = program;
        this.termWidth = termWidth;
        this.termHeight = termHeight;
        this.terms = terms;
    }

    /** The numbers of the design and its horizon colors, for the sky shader. */
    public SkyProgram program() { return program; }

    /**
     * The term images of {@link SkyProgram#terms} for the sky shader, which have the mapping of this image at half its size, as
     * IEEE half float RGBA texels row by row, one image under the other; the caller must not change them.
     */
    public short[] terms() { return terms; }

    public int termWidth() { return termWidth; }

    public int termHeight() { return termHeight; }

    /** The width of the texture of all term images, which is 1 when there are no terms, because a sampler needs a texture. */
    public int termTextureWidth() { return program.terms() == 0 ? 1 : termWidth; }

    /** The height of the texture of all term images one under the other, which is 1 when there are no terms. */
    public int termTextureHeight() { return program.terms() == 0 ? 1 : termHeight * program.terms(); }

    /** True when the sky shader draws this image: the design asks for the sharp render and the shader can draw all its layers. */
    public boolean drawsSharp() { return design.render() == SkyDesign.Render.SHARP && program.drawsSharp(); }

    /** The same pixels for a design with the same {@link SkyDesign#baked} part. */
    SkyImage withDesign(SkyDesign next) {
        return new SkyImage(argb, width, height, next, ring, program, termWidth, termHeight, terms);
    }

    /** The pixels, row by row from the top, which the caller must not change. */
    public int[] argb() { return argb; }

    public int width() { return width; }

    public int height() { return height; }

    public SkyDesign design() { return design; }

    /** A number that is different for each image, so that an adapter uploads an image only once. */
    public long serial() { return serial; }

    /** The lowest elevation in degrees that the layers of a design with this extent fill. */
    public static float layersBottom(SkyDesign.Extent extent) {
        return extent == SkyDesign.Extent.SPHERE ? BOTTOM : HORIZON_BOTTOM;
    }

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
        return fogRgb(SkyTurn.STILL, cameraYaw, brightness);
    }

    /**
     * The sRGB fog color {r, g, b} from 0 to 1 near the horizon in the direction the camera looks, in a sky that turns, where
     * the world horizon can show any part of the design.
     *
     * @param cameraYaw the Minecraft yaw in degrees, where 0 looks south and 90 looks west
     * @param brightness the factor of {@link SkyLight#brightness}, or 1
     */
    public float[] fogRgb(SkyTurn turn, float cameraYaw, float brightness) {
        float[] out = new float[3];
        if (turn.still()) {
            ringAt(ring, cameraYaw + 180, out);
        } else {
            // The middle of the band of the ring, 3 degrees above the horizon.
            double az = Math.toRadians(cameraYaw + 180), el = Math.toRadians(3);
            double[] d = new double[3];
            turn.toDesign(Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az), d);
            cellsAt(cells(), Math.toDegrees(Math.atan2(d[0], -d[2])), Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1])))), out);
        }
        for (int c = 0; c < 3; c++) out[c] = Oklab.toSrgb(out[c] * brightness) / 255f;
        return out;
    }

    private float[] cells() {
        float[] c = cells;
        if (c == null) cells = c = meanCells();
        return c;
    }

    /** The mean linear color of each cell, from a grid of at most 8 x 8 pixels in it, which costs little and misses no large part. */
    private float[] meanCells() {
        int rows = CELLS / 2;
        float[] out = new float[CELLS * rows * 3];
        int cw = Math.max(1, width / CELLS), ch = Math.max(1, height / rows);
        int sx = Math.max(1, cw / 8), sy = Math.max(1, ch / 8);
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < CELLS; col++) {
                float[] sum = new float[3];
                int n = 0;
                for (int y = r * height / rows; y < (r + 1) * height / rows; y += sy) {
                    for (int x = col * width / CELLS; x < (col + 1) * width / CELLS; x += sx) {
                        int p = argb[y * width + x];
                        sum[0] += Oklab.toLinear(p >> 16);
                        sum[1] += Oklab.toLinear(p >> 8);
                        sum[2] += Oklab.toLinear(p);
                        n++;
                    }
                }
                for (int k = 0; k < 3; k++) out[(r * CELLS + col) * 3 + k] = n == 0 ? 0 : sum[k] / n;
            }
        }
        return out;
    }

    /** The linear color at an azimuth and an elevation in degrees, mixed from the four nearest cell centers. */
    private static void cellsAt(float[] cells, double azimuth, double elevation, float[] out) {
        int rows = CELLS / 2;
        double u = ((azimuth % 360 + 360) % 360) / 360 * CELLS - 0.5, v = (TOP - elevation) / (TOP - BOTTOM) * rows - 0.5;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = (float) (u - x0), fy = (float) (v - y0);
        int xa = Math.floorMod(x0, CELLS), xb = Math.floorMod(x0 + 1, CELLS);
        int ya = Math.max(0, Math.min(rows - 1, y0)), yb = Math.max(0, Math.min(rows - 1, y0 + 1));
        for (int c = 0; c < 3; c++) {
            float top = cells[(ya * CELLS + xa) * 3 + c] * (1 - fx) + cells[(ya * CELLS + xb) * 3 + c] * fx;
            float bottom = cells[(yb * CELLS + xa) * 3 + c] * (1 - fx) + cells[(yb * CELLS + xb) * 3 + c] * fx;
            out[c] = top * (1 - fy) + bottom * fy;
        }
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
