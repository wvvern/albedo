package dev.albedo.core.sky;

/** Seeded 3D gradient noise and integer hashes, with no random number generator so that each bake gives the same pixels. */
final class Noise {
    /** The frequency step and the seed step of the octaves of fbm, and the gain of fbm, so that it goes from about -1 to 1. */
    static final float LACUNARITY = 2.03f, FBM_GAIN = 1.6f;
    static final int OCTAVE_SEED = 1013;
    /** The frequency step and the seed step of the octaves of ridged noise. */
    static final float RIDGED_LACUNARITY = 2.1f;
    static final int RIDGED_SEED = 7919;

    private Noise() {}

    /** A well mixed 32-bit hash of three integers and a seed. */
    static int hash(int x, int y, int z, int seed) {
        int h = seed * 0x27D4EB2D ^ x * 0x165667B1 ^ y * 0x1B873593 ^ z * 0x6C8E9CF5;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    /** A hash as a float from 0 (inclusive) to 1 (exclusive). */
    static float unit(int hash) {
        return (hash >>> 8) / 16777216f;
    }

    private static float grad(int h, float x, float y, float z) {
        switch (h & 15) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x + z;
            case 5: return -x + z;
            case 6: return x - z;
            case 7: return -x - z;
            case 8: return y + z;
            case 9: return -y + z;
            case 10: return y - z;
            case 11: return -y - z;
            case 12: return x + y;
            case 13: return -x + y;
            case 14: return -y + z;
            default: return -y - z;
        }
    }

    static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Gradient noise at a point, from about -1 to 1. */
    static float noise(float x, float y, float z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float xf = x - xi, yf = y - yi, zf = z - zi;
        float u = fade(xf), v = fade(yf), w = fade(zf);
        float x00 = lerp(grad(hash(xi, yi, zi, seed), xf, yf, zf), grad(hash(xi + 1, yi, zi, seed), xf - 1, yf, zf), u);
        float x10 = lerp(grad(hash(xi, yi + 1, zi, seed), xf, yf - 1, zf), grad(hash(xi + 1, yi + 1, zi, seed), xf - 1, yf - 1, zf), u);
        float x01 = lerp(grad(hash(xi, yi, zi + 1, seed), xf, yf, zf - 1), grad(hash(xi + 1, yi, zi + 1, seed), xf - 1, yf, zf - 1), u);
        float x11 = lerp(grad(hash(xi, yi + 1, zi + 1, seed), xf, yf - 1, zf - 1),
                grad(hash(xi + 1, yi + 1, zi + 1, seed), xf - 1, yf - 1, zf - 1), u);
        return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w);
    }

    /** Smooth value noise in two dimensions from 0 to 1, which repeats after {@code periodX} and {@code periodY} cells, or never on an axis of 0. */
    static float value(float x, float y, int periodX, int periodY, int seed) {
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        int x0 = repeat((int) fx, periodX), x1 = repeat((int) fx + 1, periodX), y0 = repeat((int) fy, periodY), y1 = repeat((int) fy + 1, periodY);
        float u = fade(x - fx), v = fade(y - fy);
        float a = unit(hash(x0, y0, 0, seed)), b = unit(hash(x1, y0, 0, seed));
        float c = unit(hash(x0, y1, 0, seed)), d = unit(hash(x1, y1, 0, seed));
        float top = a + (b - a) * u, bottom = c + (d - c) * u;
        return top + (bottom - top) * v;
    }

    /** The cell {@code i} of a row that repeats after {@code n} cells, or {@code i} for an {@code n} of 0. */
    static int repeat(int i, int n) {
        return n > 0 ? Math.floorMod(i, n) : i;
    }

    /**
     * The jittered point of a cell on the unit sphere into {@code out}, from the hash {@code hash} of the cell.
     *
     * @param n the cells for each unit
     * @return false when the point leaves its cell
     */
    static boolean cellPoint(int hx, int hy, int hz, int hash, float n, float[] out) {
        float vx = hx + ((hash >>> 22) + 0.5f) / 1024f;
        float vy = hy + (((hash >>> 12) & 1023) + 0.5f) / 1024f;
        float vz = hz + (((hash >>> 2) & 1023) + 0.5f) / 1024f;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-3f) return false;
        float x = vx / len, y = vy / len, z = vz / len;
        // The point of the cell moved onto the sphere, and the cell keeps it only when it stays in the cell.
        if ((int) Math.floor(x * n) != hx || (int) Math.floor(y * n) != hy || (int) Math.floor(z * n) != hz) return false;
        out[0] = x;
        out[1] = y;
        out[2] = z;
        return true;
    }

    /** Fractal sum of {@code octaves} noise layers, each at twice the frequency and half the weight, from about -1 to 1. */
    static float fbm(float x, float y, float z, int seed, int octaves) {
        return fbmSoft(x, y, z, seed, octaves, 0);
    }

    /**
     * {@link #fbm} where each octave fades out as its cell comes near the size of a pixel, so that it does not alias.
     *
     * @param footprint the width of one pixel in noise units at the first octave
     */
    static float fbmSoft(float x, float y, float z, int seed, int octaves, float footprint) {
        // The sum of the weights of all octaves, the same as in fbm, also when the fine octaves are gone.
        float sum = 0, amp = 1, norm = norm(octaves), f = footprint;
        for (int o = 0; o < octaves; o++) {
            float t = Math.max(0f, Math.min(1f, (f - 0.25f) / 0.25f));
            // Each finer octave has a larger footprint, so all octaves from here are gone.
            if (t >= 1) break;
            sum += noise(x, y, z, seed + o * OCTAVE_SEED) * amp * (1 - t * t * (3 - 2 * t));
            amp *= 0.5f;
            x *= LACUNARITY;
            y *= LACUNARITY;
            z *= LACUNARITY;
            f *= LACUNARITY;
        }
        return sum / norm * FBM_GAIN;
    }

    /**
     * The octaves from {@code first} to {@code last - 1} of {@link #fbm} with {@code octaves} octaves, so that the coarse and
     * the fine octaves can come from different places and add up to the fbm.
     */
    static float fbmPart(float x, float y, float z, int seed, int first, int last, int octaves) {
        float sum = 0, amp = 1;
        for (int o = 0; o < last; o++) {
            if (o >= first) sum += noise(x, y, z, seed + o * OCTAVE_SEED) * amp;
            amp *= 0.5f;
            x *= LACUNARITY;
            y *= LACUNARITY;
            z *= LACUNARITY;
        }
        return sum / norm(octaves) * FBM_GAIN;
    }

    /** The sum of the weights of {@code octaves} octaves of fbm. */
    private static float norm(int octaves) {
        return 2 - 2 * (float) Math.pow(0.5, octaves);
    }

    /** Ridged fractal noise: sharp bright lines where the noise crosses zero, from 0 to 1. */
    static float ridged(float x, float y, float z, int seed, int octaves) {
        float sum = 0, amp = 0.5f, norm = 0, prev = 1;
        for (int o = 0; o < octaves; o++) {
            float n = 1 - Math.abs(noise(x, y, z, seed + o * RIDGED_SEED));
            n *= n;
            sum += n * amp * prev;
            norm += amp;
            prev = n;
            amp *= 0.5f;
            x *= RIDGED_LACUNARITY;
            y *= RIDGED_LACUNARITY;
            z *= RIDGED_LACUNARITY;
        }
        return Math.min(1f, sum / norm);
    }
}
