package dev.albedo.core.sky;

/**
 * The deep field layer: far galaxies over the whole sky, each in its own grid cell as the stars are, and a cloud of the
 * stars of our own galaxy along the horizon of the layer, with the same steps as {@code albedoDeepField} in the sky shader.
 */
final class DeepField {
    /** The grid cells of the far galaxies and of the stars of the cloud for each unit of the sphere, at a layer size of 100. */
    static final float CELLS = 7f, CLOUD_CELLS = 190f;
    /** The half width of the cloud across its band and its half length along it from the layer center, in radians. */
    static final float CLOUD_WIDTH = 0.22f, CLOUD_LENGTH = 1.1f;
    /** The linear light of the cloud that the eye cannot split into stars, at a cloud of 100 percent. */
    static final float[] CLOUD_GLOW = {0.07f, 0.063f, 0.06f};

    private DeepField() {}

    /**
     * The color and alpha of the deep field in a unit direction of the layer frame.
     *
     * @param block the program block, from which layer {@code index} reads its numbers
     * @param footprint the width of one pixel in radians
     */
    static void sample(float[] block, int index, float[] p, float footprint, float[] out) {
        float scale = block[SkyProgram.at(index, SkyProgram.SCALE)];
        int seed = (int) block[SkyProgram.at(index, SkyProgram.SEED)] * 31 + 5;
        float[] light = new float[3];
        galaxies(block, index, p, footprint, scale, seed, light);
        float cloud = block[SkyProgram.at(index, SkyProgram.HEIGHT)];
        if (cloud > 0) cloud(p, footprint, scale, seed, cloud, light);
        float peak = Math.max(light[0], Math.max(light[1], light[2]));
        // The alpha carries the light up to 1 and the color carries the rest, so that an added layer adds all of the light.
        float a = Math.min(1f, peak);
        for (int c = 0; c < 3; c++) out[c] = a > 0 ? light[c] / a : 0;
        out[3] = a * Math.max(block[SkyProgram.at(index, SkyProgram.ALPHA_1)], block[SkyProgram.at(index, SkyProgram.ALPHA_2)]);
    }

    /** Adds the far galaxies near a direction: discs at each tilt and angle, the old ones in COLOR_1 and the young ones, with arms, in COLOR_2. */
    private static void galaxies(float[] block, int index, float[] p, float footprint, float scale, int seed, float[] light) {
        float chance = 0.15f + 0.85f * block[SkyProgram.at(index, SkyProgram.AMOUNT)];
        float n = CELLS / scale, reach = 0.5f * scale / CELLS;
        int bx = (int) Math.floor(p[0] * n - 0.5f), by = (int) Math.floor(p[1] * n - 0.5f), bz = (int) Math.floor(p[2] * n - 0.5f);
        for (int i = 0; i <= 1; i++) {
            for (int j = 0; j <= 1; j++) {
                for (int k = 0; k <= 1; k++) {
                    int hx = bx + i, hy = by + j, hz = bz + k;
                    if (Noise.unit(Noise.hash(hx, hy, hz, seed)) >= chance) continue;
                    int hj = Noise.hash(hx, hy, hz, seed + 1);
                    float vx = hx + ((hj >>> 22) + 0.5f) / 1024f;
                    float vy = hy + (((hj >>> 12) & 1023) + 0.5f) / 1024f;
                    float vz = hz + (((hj >>> 2) & 1023) + 0.5f) / 1024f;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if (len < 1e-3f) continue;
                    float sx = vx / len, sy = vy / len, sz = vz / len;
                    // The galaxy is the point of the cell moved onto the sphere, and the cell keeps it only when it stays in the cell.
                    if ((int) Math.floor(sx * n) != hx || (int) Math.floor(sy * n) != hy || (int) Math.floor(sz * n) != hz) continue;
                    float dx = p[0] - sx, dy = p[1] - sy, dz = p[2] - sz;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 >= reach * reach) continue;
                    int hs = Noise.hash(hx, hy, hz, seed + 2), hc = Noise.hash(hx, hy, hz, seed + 3);
                    // Few galaxies are large: the length over which the light of a disc falls is at most a fifth of the reach.
                    float big = (hs >>> 22) / 1024f;
                    float size = reach * (0.035f + 0.165f * big * big);
                    float ratio = 0.2f + 0.8f * ((hs >>> 12) & 1023) / 1024f;
                    float angle = (float) Math.PI * ((hs >>> 2) & 1023) / 1024f;
                    float young = (hc >>> 22) / 1024f;
                    float lum = 0.05f + 0.2f * ((hc >>> 12) & 1023) / 1024f;
                    // The tangent frame of the galaxy: east along the layer horizon and north toward the layer zenith.
                    float ex = sz, ez = -sx, el = (float) Math.sqrt(ex * ex + ez * ez);
                    if (el < 1e-4f) {
                        ex = 1;
                        ez = 0;
                    } else {
                        ex /= el;
                        ez /= el;
                    }
                    float east = dx * ex + dz * ez, north = dx * sy * ez + dy * (sz * ex - sx * ez) - dz * sy * ex;
                    float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                    // A disc seen from its edge is not thinner than a pixel, and darker by the same ratio.
                    float thin = Math.max(ratio, 0.7f * footprint / size);
                    float u = east * cos + north * sin, v = (north * cos - east * sin) / thin;
                    float r = (float) Math.sqrt(u * u + v * v + 0.25f * footprint * footprint);
                    float prof = (float) Math.exp(-r / size) * lum * ratio / thin;
                    // The arms of a young galaxy show only when the pixels are small enough for them.
                    float wave = 0.5f + 0.5f * (float) Math.cos(2 * ((float) Math.atan2(v, u) - 2.6f * (float) Math.log(r / size + 0.25f)));
                    float arms = SkyGenerator.smooth(0.5f, 0.6f, young) * SkyGenerator.smooth(3 * footprint, 6 * footprint, size);
                    prof *= 1 + arms * (0.9f * wave * wave - 0.45f);
                    prof *= 1 - SkyGenerator.smooth(0.8f * reach, reach, (float) Math.sqrt(d2));
                    for (int c = 0; c < 3; c++) {
                        float warm = block[SkyProgram.at(index, SkyProgram.COLOR_1) + c], blue = block[SkyProgram.at(index, SkyProgram.COLOR_2) + c];
                        light[c] += (warm + (blue - warm) * young) * prof;
                    }
                }
            }
        }
    }

    /**
     * Adds the cloud of stars of our own galaxy: a band along the horizon of the layer that is brightest at the layer center,
     * with a soft glow, a dark rift of dust through it and many faint stars.
     */
    private static void cloud(float[] p, float footprint, float scale, int seed, float cloud, float[] light) {
        float lat = (float) Math.asin(Math.max(-1f, Math.min(1f, p[1]))) / CLOUD_WIDTH;
        float band = (float) Math.exp(-lat * lat);
        if (band < 0.003f) return;
        float az = (float) Math.atan2(p[0], -p[2]) / CLOUD_LENGTH;
        float shape = Noise.fbm(p[0] * 3.2f, p[1] * 3.2f, p[2] * 3.2f, seed + 11, 5);
        float body = band * (0.3f + 0.7f * (float) Math.exp(-az * az)) * SkyGenerator.smooth(-0.45f, 0.35f, shape + 0.15f);
        float bend = 0.3f * shape;
        float rift = SkyGenerator.smooth(0.35f, 0.8f, Noise.ridged(p[0] * 3.6f + bend, p[1] * 3.6f + bend, p[2] * 3.6f + bend, seed + 12, 4));
        body *= 1 - 0.8f * rift;
        float glow = cloud * body * (0.7f + 0.5f * Noise.fbmSoft(p[0] * 14f, p[1] * 14f, p[2] * 14f, seed + 13, 4, footprint * 14f));
        for (int c = 0; c < 3; c++) light[c] += glow * CLOUD_GLOW[c];
        stars(p, footprint, scale, seed + 14, 0.95f * body, cloud, light);
    }

    /** Adds the faint stars of the cloud near a direction, where {@code density} is the chance that a cell has a star. */
    private static void stars(float[] p, float footprint, float scale, int seed, float density, float cloud, float[] light) {
        float n = CLOUD_CELLS / scale, reach = 0.5f * scale / CLOUD_CELLS;
        float blur = SkyStars.PIXEL * footprint, core = SkyStars.CORE * scale, core2 = core * core + blur * blur;
        float[] color = new float[3];
        int bx = (int) Math.floor(p[0] * n - 0.5f), by = (int) Math.floor(p[1] * n - 0.5f), bz = (int) Math.floor(p[2] * n - 0.5f);
        for (int i = 0; i <= 1; i++) {
            for (int j = 0; j <= 1; j++) {
                for (int k = 0; k <= 1; k++) {
                    int hx = bx + i, hy = by + j, hz = bz + k;
                    int h = Noise.hash(hx, hy, hz, seed);
                    if ((h >>> 24) / 256f >= density) continue;
                    int hj = Noise.hash(hx, hy, hz, seed + 1);
                    float vx = hx + ((hj >>> 22) + 0.5f) / 1024f;
                    float vy = hy + (((hj >>> 12) & 1023) + 0.5f) / 1024f;
                    float vz = hz + (((hj >>> 2) & 1023) + 0.5f) / 1024f;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if (len < 1e-3f) continue;
                    float sx = vx / len, sy = vy / len, sz = vz / len;
                    if ((int) Math.floor(sx * n) != hx || (int) Math.floor(sy * n) != hy || (int) Math.floor(sz * n) != hz) continue;
                    float dx = p[0] - sx, dy = p[1] - sy, dz = p[2] - sz;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 >= reach * reach) continue;
                    float flux = Math.min(0.7f, 0.03f * (float) Math.pow((((h >>> 12) & 4095) + 0.5f) / 4096f, -1 / 1.3f));
                    float glow = cloud * 2 * flux * core / (float) Math.sqrt(core2) * (float) Math.exp(-d2 / (2 * core2))
                            * (1 - SkyGenerator.smooth(0.8f * reach, reach, (float) Math.sqrt(d2)));
                    SkyStars.color(((h & 4095) + 0.5f) / 4096f, color, 0);
                    for (int c = 0; c < 3; c++) light[c] += color[c] * glow;
                }
            }
        }
    }
}
