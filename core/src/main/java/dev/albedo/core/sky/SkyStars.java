package dev.albedo.core.sky;

/**
 * Java twin of {@code albedoStars}, where all sizes are angles in radians and the footprint (the angle of one pixel)
 * widens only the parts thinner than a pixel.
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
        /** The hash salt of this grid. */
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

    /**
     * The floats of one star from {@link #star}: the unit direction, the light, the place along the star classes, and the two
     * frequencies in hertz and two phases in turns of its twinkle.
     */
    static final int STAR_FLOATS = 9;
    /** The floats of the room that {@link #add} uses: the light of the rim and the spikes, and the color, for each channel. */
    static final int WORK_FLOATS = 6;

    static final float RADIAN = (float) (Math.PI / 180);
    /** The standard deviation of the core of each star. */
    static final float CORE = 0.009f * RADIAN;
    /** The part of a pixel that the footprint adds to the width of a thin part, as a standard deviation. */
    static final float PIXEL = 0.42f;
    /** The radius of the halo of a star of light 1, which grows with the square root of the light. */
    static final float HALO = 0.03f * RADIAN;
    /** The part of the light of a star in its halo. */
    static final float HALO_LIGHT = 0.025f;
    /** The radius of the rim of a star of light 1, which grows as the halo, and the standard deviation of its width. */
    static final float RIM = 0.016f * RADIAN, RIM_WIDTH = 0.006f * RADIAN;
    /** The part of the light of a star in its rim. */
    static final float RIM_LIGHT = 0.03f;
    /** The light from which a star has spikes. */
    static final float SPIKE_FLUX = 2f;
    /** The length of the spikes of the brightest star, which is less than the room that its large cell gives. */
    static final float SPIKE_LENGTH = 1.4f * RADIAN;
    /** The standard deviation of the width of a spike. */
    static final float SPIKE_WIDTH = 0.004f * RADIAN;
    /** The light of a spike at the center of the star, for a spikes value of 1 and a star of light 1. */
    static final float SPIKE_LIGHT = 0.45f;
    /** The size of the red, green and blue parts of the rim and the spikes, because diffraction spreads long waves more. */
    static final float[] RIM_WAVE = {1.08f, 1f, 0.9f}, SPIKE_WAVE = {1.04f, 1f, 0.95f};
    /** The directions of the spike lines in the tangent plane: three at 60 degrees with one vertical, and one horizontal. */
    static final float[] SPIKE_X = {0f, 0.8660254f, -0.8660254f, 1f}, SPIKE_Y = {1f, 0.5f, 0.5f, 0f};
    /** The light and the length of each spike line, where the horizontal line is fainter and shorter. */
    static final float[] SPIKE_GAIN = {1f, 1f, 1f, 0.5f}, SPIKE_REACH = {1f, 1f, 1f, 0.7f};
    /** The part of the stars of each class (O, B, A, F, G, K, M) among the stars that the eye sees. */
    static final float[] CLASS_SHARE = {0.02f, 0.12f, 0.2f, 0.17f, 0.16f, 0.25f, 0.08f};
    /** The linear colors of black bodies at the class boundaries, from 40000 K (sRGB 9BB0FF) to 2600 K (sRGB FFA050). */
    static final float[] CLASS_RGB = {
        0.3278f, 0.4342f, 1f, 0.4020f, 0.5210f, 1f, 0.5906f, 0.6795f, 1f, 0.9387f, 0.9301f, 1f,
        1f, 0.9047f, 0.8228f, 1f, 0.6445f, 0.3564f, 1f, 0.4969f, 0.1981f, 1f, 0.3515f, 0.0802f};
    /** How much of the black body color a star keeps, with the rest white, so that the tints are slight. */
    static final float TINT = 0.7f;

    /** The change of the light of a star at the zenith and the more change near the horizon, at a twinkle of 1. */
    static final float TWINKLE_ZENITH = 0.1f, TWINKLE_HORIZON = 0.3f;
    /** The change of red against blue for each part of twinkle near the horizon, where the air spreads the colors. */
    static final float TWINKLE_COLOR = 0.5f;

    /** The standard deviation of the width of a trail for each pixel of footprint, more than the core so that a thin arc is not dotted. */
    static final float TRAIL_PIXEL = 0.6f;
    /** The distance across a trail, in standard deviations of its width, at which its light ends. */
    static final float TRAIL_REACH = 4f;
    /** The light of a trail at a trail light of 1, for each unit of the root of the light of its star. */
    static final float TRAIL_GAIN = 1f;
    /** The arc of a faint star at a layer size of 100 at which it keeps half of its light in a sky with trails. */
    static final float FIELD_SPREAD = 1.5f * RADIAN;

    private final float density, spikes, scale;
    private final int seed;
    /** The twinkle from 0 to 1, the time in seconds, and the zenith of the sky in the layer frame. */
    private final float twinkle, time;
    private final float[] up;
    /** The trail arc in radians (0 for no trails), the trail light, and the trail axis in the layer frame. */
    private final float trail, trailLight;
    private final float[] axis;

    /**
     * @param amount the count from 0 to 1
     * @param spikes the strength of the spikes from 0 to 2
     * @param scale the layer size, where 1 is 100 percent
     * @param twinkle how much the stars twinkle, from 0 to 1
     * @param time the seconds of the sky clock, 0 for the bake
     * @param up the unit direction of the sky zenith in the layer frame, which the twinkle uses to find the horizon
     * @param trail the arc of each trail in radians, 0 for no trails
     * @param trailLight the light of the trails from 0 to 1
     * @param axis the unit trail axis in the layer frame, around which a positive angle goes back along the trail
     */
    private SkyStars(float amount, float spikes, float scale, int seed, float twinkle, float time, float[] up, float trail, float trailLight,
            float[] axis) {
        float m = 0.2f + 0.8f * amount;
        density = m * m;
        this.spikes = spikes;
        this.scale = scale;
        this.seed = seed;
        this.twinkle = twinkle;
        this.time = time;
        this.up = up.clone();
        this.trail = trail;
        this.trailLight = trailLight;
        this.axis = axis.clone();
    }

    /**
     * The stars of layer {@code index} of a program block.
     *
     * @param up the unit direction of the sky zenith in the layer frame
     * @param time the seconds of the sky clock, 0 for the bake
     */
    static SkyStars of(float[] block, int index, float[] up, float time) {
        float[] axis = new float[3];
        for (int c = 0; c < 3; c++) axis[c] = block[SkyProgram.index(index, SkyProgram.TRAIL_AXIS + c * 4)];
        return new SkyStars(block[SkyProgram.index(index, SkyProgram.AMOUNT)], block[SkyProgram.index(index, SkyProgram.HEIGHT)] * 2,
                block[SkyProgram.index(index, SkyProgram.SCALE)], (int) block[SkyProgram.index(index, SkyProgram.SEED)],
                block[SkyProgram.index(index, SkyProgram.TWINKLE)], time, up, block[SkyProgram.index(index, SkyProgram.TRAIL)],
                block[SkyProgram.index(index, SkyProgram.TRAIL_LIGHT)], axis);
    }

    /** The linear light of the stars in the unit direction {@code p} of the layer frame, as color and alpha into {@code out}. */
    void shade(float[] p, float footprint, float[] tint, float alpha, float[] out) {
        float[] light = new float[3];
        light(p, footprint, light);
        for (int c = 0; c < 3; c++) light[c] *= tint[c];
        SkyGenerator.lightAlpha(light, tint, alpha, out);
    }

    /** Adds the linear light of the stars of both tiers in the unit direction {@code p} to {@code light}. */
    void light(float[] p, float footprint, float[] light) {
        float[] star = new float[STAR_FLOATS], work = new float[WORK_FLOATS];
        for (Tier t : Tier.values()) {
            float n = t.cells / scale;
            // The 2 x 2 x 2 cells nearest to the point, because no star reaches into a cell that is not next to its own.
            int bx = (int) Math.floor(p[0] * n - 0.5f), by = (int) Math.floor(p[1] * n - 0.5f), bz = (int) Math.floor(p[2] * n - 0.5f);
            for (int i = 0; i <= 1; i++) {
                for (int j = 0; j <= 1; j++) {
                    for (int k = 0; k <= 1; k++) if (star(t, bx + i, by + j, bz + k, star)) add(t, star, p, footprint, work, light);
                }
            }
        }
        if (trail > 0) trails(p, footprint, star, work, light);
    }

    /**
     * Adds the light of the trails of the bright stars at {@code p} to {@code light}, where each trail is the arc that its
     * star goes along around the trail axis, which fades from the star to its end.
     */
    void trails(float[] p, float footprint, float[] star, float[] work, float[] light) {
        Tier t = Tier.BRIGHT;
        float n = t.cells / scale;
        float ax = axis[0], ay = axis[1], az = axis[2];
        // The point as its height along the axis and its offset across it, the radius of its circle around the axis.
        float h = p[0] * ax + p[1] * ay + p[2] * az;
        float rx = p[0] - ax * h, ry = p[1] - ay * h, rz = p[2] - az * h;
        float radius = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (radius < 1e-5f) return;
        // The side vector goes along the circle, so that the point turned back by an angle a is h axis + cos(a) r - sin(a) side.
        float sx = ay * rz - az * ry, sy = az * rx - ax * rz, sz = ax * ry - ay * rx;
        float cut = TRAIL_REACH * trailWidth(footprint);
        // Each part of the arc is at most one cell long, so that the box of cells around its chord is small.
        int parts = Math.max(1, (int) Math.ceil(trail * radius * n));
        float step = trail / parts;
        float margin = (cut + radius * (1 - (float) Math.cos(step * 0.5f))) * n + 0.01f;
        float lx = p[0] * n, ly = p[1] * n, lz = p[2] * n;
        for (int part = 0; part < parts; part++) {
            float a = (part + 1) * step, c = (float) Math.cos(a), s = (float) Math.sin(a);
            float qx = (ax * h + rx * c - sx * s) * n, qy = (ay * h + ry * c - sy * s) * n, qz = (az * h + rz * c - sz * s) * n;
            int x0 = (int) Math.floor(Math.min(lx, qx) - margin), x1 = (int) Math.floor(Math.max(lx, qx) + margin);
            int y0 = (int) Math.floor(Math.min(ly, qy) - margin), y1 = (int) Math.floor(Math.max(ly, qy) + margin);
            int z0 = (int) Math.floor(Math.min(lz, qz) - margin), z1 = (int) Math.floor(Math.max(lz, qz) + margin);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        if (!star(t, x, y, z, star)) continue;
                        // The height difference is never more than the angle difference, so a star that it puts far away is far away.
                        float sh = star[0] * ax + star[1] * ay + star[2] * az;
                        if (Math.abs(sh - h) >= cut) continue;
                        float back = (float) Math.atan2(-(star[0] * sx + star[1] * sy + star[2] * sz), star[0] * rx + star[1] * ry + star[2] * rz);
                        // Only the part whose piece of the arc holds the star adds it, so that no star adds its trail twice.
                        if (back <= 0 || back >= trail || Math.min(parts - 1, (int) (back / step)) != part) continue;
                        addTrail(star, footprint, work, light, h, radius, back);
                    }
                }
            }
            lx = qx;
            ly = qy;
            lz = qz;
        }
    }

    /**
     * The part of its light that a faint star keeps in a sky with trails, because its light spreads along its arc, which is
     * long far from the axis, and its trail is too faint to see.
     */
    float spread(float[] s) {
        float h = s[0] * axis[0] + s[1] * axis[1] + s[2] * axis[2];
        float arc = trail * (float) Math.sqrt(Math.max(0f, 1 - h * h)) / (FIELD_SPREAD * scale);
        return 1 / (1 + arc * arc);
    }

    /** The standard deviation of the width of a trail: the core of a star, widened by the footprint. */
    private float trailWidth(float footprint) {
        float core = CORE * scale, blur = TRAIL_PIXEL * footprint;
        return (float) Math.sqrt(core * core + blur * blur);
    }

    /**
     * Adds the light at {@code p} of the trail of the star {@code s} to {@code light}, which is 0 ahead of the star, past the
     * end of the trail and from {@link #TRAIL_REACH} widths across it.
     */
    void addTrail(float[] s, float[] p, float footprint, float[] work, float[] light) {
        float ax = axis[0], ay = axis[1], az = axis[2];
        float h = p[0] * ax + p[1] * ay + p[2] * az;
        float rx = p[0] - ax * h, ry = p[1] - ay * h, rz = p[2] - az * h;
        float radius = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (radius < 1e-5f) return;
        float sx = ay * rz - az * ry, sy = az * rx - ax * rz, sz = ax * ry - ay * rx;
        // The angle around the axis from the star back to the point, which is the age of this part of the trail.
        float back = (float) Math.atan2(-(s[0] * sx + s[1] * sy + s[2] * sz), s[0] * rx + s[1] * ry + s[2] * rz);
        addTrail(s, footprint, work, light, h, radius, back);
    }

    /**
     * {@link #addTrail(float[], float[], float, float[], float[])} with the place of the point around the axis: its height
     * {@code h} along the axis, its distance {@code radius} from the axis, and the angle {@code back} around the axis from the
     * star back to it.
     */
    private void addTrail(float[] s, float footprint, float[] work, float[] light, float h, float radius, float back) {
        if (back <= 0 || back >= trail) return;
        float ax = axis[0], ay = axis[1], az = axis[2];
        float sh = s[0] * ax + s[1] * ay + s[2] * az;
        float width = trailWidth(footprint), cut = TRAIL_REACH * width;
        // The distance across the trail is the difference of the angles from the axis of the star and the point.
        float d = (float) (Math.atan2(Math.sqrt(Math.max(0f, 1 - sh * sh)), sh) - Math.atan2(radius, h)), ad = Math.abs(d);
        if (ad >= cut) return;
        float across = (float) Math.exp(-d * d / (2 * width * width)) * (1 - SkyMath.smooth(0.75f * cut, cut, ad));
        float x = back / trail, fade = (1 - x) * (1 - x);
        // The trail starts in the core of the star and fades to nothing at its end.
        float start = SkyMath.smooth(0, 3 * width, back * radius);
        // The peak falls with the width, as the core of a star does, so that a trail keeps its light at each footprint.
        float k = trailLight * TRAIL_GAIN * CORE * scale / width * (float) Math.sqrt(s[3]) * across * fade * start;
        color(s[4], work, 3);
        for (int c = 0; c < 3; c++) light[c] += work[3 + c] * k;
    }

    /** The star of a cell into {@code out}, or false when the cell has no star. */
    boolean star(Tier t, int hx, int hy, int hz, float[] out) {
        int h = Noise.hash(hx, hy, hz, seed * 31 + t.salt);
        float chance = (h >>> 24) / 256f;
        if (chance >= density) return false;
        if (!Noise.cellPoint(hx, hy, hz, Noise.hash(hx, hy, hz, seed * 31 + t.salt + 1), t.cells / scale, out)) return false;
        float u = (((h >>> 12) & 4095) + 0.5f) / 4096f;
        out[3] = Math.min(t.maxFlux, t.minFlux * (float) Math.pow(u, -1 / t.slope));
        out[4] = ((h & 4095) + 0.5f) / 4096f;
        if (twinkle > 0 && t.detail) {
            // The frequencies are whole numbers of turns in the hour of the sky clock, so that no star jumps when the clock starts again.
            int k = Noise.hash(hx, hy, hz, seed * 31 + t.salt + 2);
            out[5] = 0.5f + (k >>> 24) / 225f;
            out[6] = 1.3f + ((k >>> 16) & 255) / 150f;
            out[7] = ((k >>> 8) & 255) / 256f;
            out[8] = (k & 255) / 256f;
        }
        return true;
    }

    /**
     * Adds the light of one star in the direction {@code p} to {@code light}, which is 0 from half a cell of its tier.
     *
     * @param work {@link #WORK_FLOATS} scratch floats for the rim, the spikes and the color
     */
    void add(Tier t, float[] s, float[] p, float footprint, float[] work, float[] light) {
        float dx = p[0] - s[0], dy = p[1] - s[1], dz = p[2] - s[2];
        float d2 = dx * dx + dy * dy + dz * dz;
        float reach = 0.5f * scale / t.cells;
        if (d2 >= reach * reach) return;
        float d = (float) Math.sqrt(d2), flux = s[3], tint = 0;
        if (trail > 0 && !t.detail) flux *= spread(s);
        if (twinkle > 0 && t.detail) {
            // A bright star twinkles more near the horizon, where its light goes through more air.
            float air = 1 - SkyMath.smooth(0.05f, 0.75f, s[0] * up[0] + s[1] * up[1] + s[2] * up[2]);
            float change = twinkle * (TWINKLE_ZENITH + TWINKLE_HORIZON * air) * (0.35f + 0.65f * SkyMath.smooth(0.2f, 6f, flux));
            tint = TWINKLE_COLOR * change * air * SkyMath.wave((s[6] - s[5]) * time + s[7] + s[8]);
            flux *= 1 + change * (0.6f * SkyMath.wave(s[5] * time + s[7]) + 0.4f * SkyMath.wave(s[6] * time + s[8]));
        }
        float blur = PIXEL * footprint, blur2 = blur * blur;
        // The footprint widens the core, and its peak falls with the width, not the area, so that a star smaller than a pixel stays visible.
        float core = CORE * scale, core2 = core * core + blur2;
        float glow = flux * core / (float) Math.sqrt(core2) * (float) Math.exp(-d2 / (2 * core2));
        float halo = HALO * scale * (float) Math.sqrt(flux), halo2 = halo * halo + blur2;
        float q = 1 + d2 / halo2;
        glow += flux * HALO_LIGHT * halo * halo / halo2 / (q * q);
        // The rim and the spikes add their light for each channel into the first three floats, and the color goes into the last three.
        for (int c = 0; c < 3; c++) work[c] = 0;
        if (t.detail) {
            rim(flux, d, blur2, work);
            // The spikes keep the length of the still star and change with the root of the twinkle, so that they never flicker on and off.
            if (spikes > 0 && s[3] > SPIKE_FLUX) spikes(t, s, dx, dy, dz, blur2, (float) Math.sqrt(flux / s[3]), work);
        }
        color(s[4], work, 3);
        // The air spreads the colors of a star near the horizon, so that its red and blue change against each other.
        work[3] *= 1 + tint;
        work[5] *= 1 - tint;
        // The light goes to 0 before the edge of the reach, so that no star is cut.
        float fade = 1 - SkyMath.smooth(0.8f * reach, reach, d);
        for (int c = 0; c < 3; c++) light[c] += work[3 + c] * fade * (glow + work[c]);
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

    /** The linear color of a star at {@code u} along the classes into {@code out} from {@code at}: its class colors mixed with white. */
    static void color(float u, float[] out, int at) {
        int i = 0;
        while (i < CLASS_SHARE.length - 1 && u >= CLASS_SHARE[i]) {
            u -= CLASS_SHARE[i];
            i++;
        }
        float f = Math.min(1f, u / CLASS_SHARE[i]);
        for (int c = 0; c < 3; c++) {
            float a = CLASS_RGB[i * 3 + c], b = CLASS_RGB[i * 3 + 3 + c];
            out[at + c] = 1 - TINT + TINT * (a + (b - a) * f);
        }
    }

    /**
     * Adds the light of the spikes of a bright star at the offset {@code (dx, dy, dz)} from it to {@code out} for each channel.
     *
     * @param boost the factor of the light of the spikes from the twinkle
     */
    private void spikes(Tier t, float[] s, float dx, float dy, float dz, float blur2, float boost, float[] out) {
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
        float gain = spikes * boost * SPIKE_LIGHT * (float) Math.sqrt(flux);
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
}
