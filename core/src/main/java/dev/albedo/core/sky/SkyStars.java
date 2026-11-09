package dev.albedo.core.sky;

/**
 * The stars generator, with the same steps as {@code albedoStars} in the sky shader: many faint points and few bright stars
 * with the colors of real stars, each with a hard core, a thin colored rim, a soft halo and, when bright, thin diffraction
 * spikes, where all sizes are angles and the footprint (the angle of one pixel) widens only the parts thinner than a pixel.
 */
final class SkyStars {
    /** One grid of stars, where a star stays in its cell and reaches at most half a cell, so a search of 2 x 2 x 2 cells finds all. */
    enum Tier {
        /** Many faint stars in small cells, with only a core and a halo. */
        FIELD(88f, 0.04f, 0.9f, 1.3f, 7, false),
        /** Few bright stars in large cells, which have room for their halos and spikes. */
        BRIGHT(16f, 0.9f, 60f, 1.2f, 13, true);

        /** The cells for each radian at a layer size of 100. */
        final float cells;
        /** The light of the faintest and the brightest star, where a light of 1 fills a channel at the core. */
        final float minFlux, maxFlux;
        /** The power of the brightness spread: the count of stars brighter than a light falls as the light to this power. */
        final float slope;
        /** The value that makes the hashes of this grid different from the hashes of the other grid. */
        final int salt;
        /** True when the stars have a rim and spikes, which only bright stars show. */
        final boolean detail;

        Tier(float cells, float minFlux, float maxFlux, float slope, int salt, boolean detail) {
            this.cells = cells;
            this.minFlux = minFlux;
            this.maxFlux = maxFlux;
            this.slope = slope;
            this.salt = salt;
            this.detail = detail;
        }
    }

    /** The floats of one star from {@link #star}: the unit direction, the light, and the place along the star classes. */
    static final int STAR_FLOATS = 5;

    private static final float RADIAN = (float) (Math.PI / 180);
    /** The standard deviation of the core of each star. */
    private static final float CORE = 0.009f * RADIAN;
    /** The part of a pixel that the footprint adds to the width of a thin part, as a standard deviation. */
    private static final float PIXEL = 0.42f;
    /** The radius of the halo of a star of light 1, which grows with the square root of the light. */
    private static final float HALO = 0.03f * RADIAN;
    /** The part of the light of a star in its halo. */
    private static final float HALO_LIGHT = 0.025f;
    /** The radius of the rim of a star of light 1, which grows as the halo, and the standard deviation of its width. */
    private static final float RIM = 0.016f * RADIAN, RIM_WIDTH = 0.006f * RADIAN;
    /** The part of the light of a star in its rim. */
    private static final float RIM_LIGHT = 0.03f;
    /** The light from which a star has spikes. */
    private static final float SPIKE_FLUX = 2f;
    /** The length of the spikes of the brightest star, which is less than the room that its large cell gives. */
    private static final float SPIKE_LENGTH = 1.4f * RADIAN;
    /** The standard deviation of the width of a spike. */
    private static final float SPIKE_WIDTH = 0.004f * RADIAN;
    /** The light of a spike at the center of the star, for a spikes value of 1 and a star of light 1. */
    private static final float SPIKE_LIGHT = 0.45f;
    /** The size of the red, green and blue parts of the rim and the spikes, because diffraction spreads long waves more. */
    private static final float[] RIM_WAVE = {1.08f, 1f, 0.9f}, SPIKE_WAVE = {1.04f, 1f, 0.95f};
    /** The directions of the spike lines in the tangent plane: three at 60 degrees with one vertical, and one horizontal. */
    private static final float[] SPIKE_X = {0f, 0.8660254f, -0.8660254f, 1f}, SPIKE_Y = {1f, 0.5f, 0.5f, 0f};
    /** The light and the length of each spike line, where the horizontal line is fainter and shorter. */
    private static final float[] SPIKE_GAIN = {1f, 1f, 1f, 0.5f}, SPIKE_REACH = {1f, 1f, 1f, 0.7f};
    /** The part of the stars of each class (O, B, A, F, G, K, M) among the stars that the eye sees. */
    private static final float[] CLASS_SHARE = {0.02f, 0.12f, 0.2f, 0.17f, 0.16f, 0.25f, 0.08f};
    /** The linear colors of black bodies at the class boundaries, from 40000 K (sRGB 9BB0FF) to 2600 K (sRGB FFA050). */
    private static final float[] CLASS_RGB = {
        0.3278f, 0.4342f, 1f, 0.4020f, 0.5210f, 1f, 0.5906f, 0.6795f, 1f, 0.9387f, 0.9301f, 1f,
        1f, 0.9047f, 0.8228f, 1f, 0.6445f, 0.3564f, 1f, 0.4969f, 0.1981f, 1f, 0.3515f, 0.0802f};
    /** How much of the black body color a star keeps, with the rest white, so that the tints are slight. */
    private static final float TINT = 0.7f;
    private static final float SQRT3 = 1.7320508f;

    private final float density, spikes, scale;
    private final int seed;

    /**
     * @param amount the count from 0 to 1
     * @param spikes the strength of the spikes from 0 to 2
     * @param scale the layer size, where 1 is 100 percent
     */
    SkyStars(float amount, float spikes, float scale, int seed) {
        float m = 0.2f + 0.8f * amount;
        density = m * m;
        this.spikes = spikes;
        this.scale = scale;
        this.seed = seed;
    }

    /** The linear light of the stars in the unit direction {@code p} of the layer frame, as color and alpha into {@code out}. */
    void shade(float[] p, float footprint, float[] tint, float alpha, float[] out) {
        float[] light = new float[3];
        light(p, footprint, light);
        float peak = 0;
        for (int c = 0; c < 3; c++) {
            light[c] *= tint[c];
            peak = Math.max(peak, light[c]);
        }
        // The alpha carries the light up to 1 and the color carries the rest, so that an added layer adds all of the light.
        float a = Math.min(1f, peak);
        for (int c = 0; c < 3; c++) out[c] = a > 0 ? light[c] / a : tint[c];
        out[3] = a * alpha;
    }

    /** Adds the linear light of the stars of both tiers in the unit direction {@code p} to {@code light}. */
    void light(float[] p, float footprint, float[] light) {
        float[] star = new float[STAR_FLOATS];
        for (Tier t : Tier.values()) {
            float n = t.cells / scale;
            // The 2 x 2 x 2 cells nearest to the point, because no star reaches into a cell that is not next to its own.
            int bx = (int) Math.floor(p[0] * n - 0.5f), by = (int) Math.floor(p[1] * n - 0.5f), bz = (int) Math.floor(p[2] * n - 0.5f);
            for (int i = 0; i <= 1; i++) {
                for (int j = 0; j <= 1; j++) {
                    for (int k = 0; k <= 1; k++) if (star(t, bx + i, by + j, bz + k, star)) add(t, star, p, footprint, light);
                }
            }
        }
    }

    /** The star of a cell into {@code out}, or false when the cell has no star. */
    boolean star(Tier t, int hx, int hy, int hz, float[] out) {
        int h = Noise.hash(hx, hy, hz, seed * 31 + t.salt);
        float chance = (h >>> 24) / 256f;
        if (chance >= density) return false;
        int j = Noise.hash(hx, hy, hz, seed * 31 + t.salt + 1);
        float vx = hx + ((j >>> 22) + 0.5f) / 1024f;
        float vy = hy + (((j >>> 12) & 1023) + 0.5f) / 1024f;
        float vz = hz + (((j >>> 2) & 1023) + 0.5f) / 1024f;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-3f) return false;
        float x = vx / len, y = vy / len, z = vz / len, n = t.cells / scale;
        // The star is the point of the cell moved onto the sphere, and the cell keeps it only when it stays in the cell.
        if ((int) Math.floor(x * n) != hx || (int) Math.floor(y * n) != hy || (int) Math.floor(z * n) != hz) return false;
        // The sphere crosses more cells near a diagonal of the grid, so fewer of them keep a star there.
        if (chance >= density * (Math.abs(x) + Math.abs(y) + Math.abs(z)) / SQRT3) return false;
        out[0] = x;
        out[1] = y;
        out[2] = z;
        float u = (((h >>> 12) & 4095) + 0.5f) / 4096f;
        out[3] = Math.min(t.maxFlux, t.minFlux * (float) Math.pow(u, -1 / t.slope));
        out[4] = ((h & 4095) + 0.5f) / 4096f;
        return true;
    }

    /** Adds the light of one star in the direction {@code p} to {@code light}, which is 0 from half a cell of its tier. */
    void add(Tier t, float[] s, float[] p, float footprint, float[] light) {
        float dx = p[0] - s[0], dy = p[1] - s[1], dz = p[2] - s[2];
        float d2 = dx * dx + dy * dy + dz * dz;
        float reach = 0.5f * scale / t.cells;
        if (d2 >= reach * reach) return;
        float d = (float) Math.sqrt(d2), flux = s[3];
        float blur = PIXEL * footprint, blur2 = blur * blur;
        // The core keeps its light when the footprint widens it, so that a star smaller than a pixel does not flicker.
        float core = CORE * scale, core2 = core * core + blur2;
        float glow = flux * core / (float) Math.sqrt(core2) * (float) Math.exp(-d2 / (2 * core2));
        float halo = HALO * scale * (float) Math.sqrt(flux), halo2 = halo * halo + blur2;
        float q = 1 + d2 / halo2;
        glow += flux * HALO_LIGHT * halo * halo / halo2 / (q * q);
        float[] detail = new float[3], color = new float[3];
        if (t.detail) {
            rim(flux, d, blur2, detail);
            if (spikes > 0 && flux > SPIKE_FLUX) spikes(t, s, dx, dy, dz, blur2, detail);
        }
        color(s[4], color);
        // The light goes to 0 before the edge of the reach, so that no star is cut.
        float fade = 1 - smooth(0.8f * reach, reach, d);
        for (int c = 0; c < 3; c++) light[c] += color[c] * fade * (glow + detail[c]);
    }

    /** Adds the light of the thin rim of a star at the distance {@code d} from it to {@code out}, with red outside and blue inside. */
    private void rim(float flux, float d, float blur2, float[] out) {
        float width = RIM_WIDTH * scale, wide = (float) Math.sqrt(width * width + blur2);
        float radius = RIM * scale * (float) Math.sqrt(flux);
        float light = flux * RIM_LIGHT * width / wide;
        for (int c = 0; c < 3; c++) {
            float r = (d - radius * RIM_WAVE[c]) / wide;
            out[c] += light * (float) Math.exp(-0.5f * r * r);
        }
    }

    /** The linear color of a star at {@code u} along the classes, a mix of the two boundary colors of its class with white. */
    static void color(float u, float[] out) {
        int i = 0;
        while (i < CLASS_SHARE.length - 1 && u >= CLASS_SHARE[i]) {
            u -= CLASS_SHARE[i];
            i++;
        }
        float f = Math.min(1f, u / CLASS_SHARE[i]);
        for (int c = 0; c < 3; c++) {
            float a = CLASS_RGB[i * 3 + c], b = CLASS_RGB[i * 3 + 3 + c];
            out[c] = 1 - TINT + TINT * (a + (b - a) * f);
        }
    }

    /** Adds the light of the spikes of a bright star at the offset {@code (dx, dy, dz)} from it to {@code out} for each channel. */
    private void spikes(Tier t, float[] s, float dx, float dy, float dz, float blur2, float[] out) {
        float flux = s[3];
        float length = SPIKE_LENGTH * scale * (float) Math.sqrt((flux - SPIKE_FLUX) / (t.maxFlux - SPIKE_FLUX));
        // The tangent frame of the star: east along the layer horizon and north toward the layer zenith, so the spikes do not turn.
        float ex = s[2], ez = -s[0], el = (float) Math.sqrt(ex * ex + ez * ez);
        if (el < 1e-4f) {
            ex = 1;
            ez = 0;
        } else {
            ex /= el;
            ez /= el;
        }
        float nx = s[1] * ez, ny = s[2] * ex - s[0] * ez, nz = -s[1] * ex;
        float u = dx * ex + dz * ez, v = dx * nx + dy * ny + dz * nz;
        float gain = spikes * SPIKE_LIGHT * (float) Math.sqrt(flux);
        for (int line = 0; line < SPIKE_X.length; line++) {
            float along = Math.abs(u * SPIKE_X[line] + v * SPIKE_Y[line]);
            float across = v * SPIKE_X[line] - u * SPIKE_Y[line];
            for (int c = 0; c < 3; c++) {
                float reach = length * SPIKE_REACH[line] * SPIKE_WAVE[c];
                if (along >= reach) continue;
                float taper = 1 - along / reach;
                float width = SPIKE_WIDTH * scale * SPIKE_WAVE[c], wide = (float) Math.sqrt(width * width + blur2);
                float x = across / wide;
                out[c] += gain * SPIKE_GAIN[line] * taper * taper * taper * width / wide * (float) Math.exp(-0.5f * x * x);
            }
        }
    }

    private static float smooth(float a, float b, float t) {
        float x = Math.max(0f, Math.min(1f, (t - a) / (b - a)));
        return x * x * (3 - 2 * x);
    }
}
