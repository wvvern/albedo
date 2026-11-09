package dev.albedo.core.sky;

/** Seeded 3D gradient noise and integer hashes, with no random number generator so that each bake gives the same pixels. */
final class Noise {
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

    private static float fade(float t) {
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

    /** Fractal sum of {@code octaves} noise layers, each at twice the frequency and half the weight, from about -1 to 1. */
    static float fbm(float x, float y, float z, int seed, int octaves) {
        float sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += noise(x, y, z, seed + o * 1013) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2.03f;
            y *= 2.03f;
            z *= 2.03f;
        }
        return sum / norm * 1.6f;
    }

    /** Ridged fractal noise: sharp bright lines where the noise crosses zero, from 0 to 1. */
    static float ridged(float x, float y, float z, int seed, int octaves) {
        float sum = 0, amp = 0.5f, norm = 0, prev = 1;
        for (int o = 0; o < octaves; o++) {
            float n = 1 - Math.abs(noise(x, y, z, seed + o * 7919));
            n *= n;
            sum += n * amp * prev;
            norm += amp;
            prev = n;
            amp *= 0.5f;
            x *= 2.1f;
            y *= 2.1f;
            z *= 2.1f;
        }
        return Math.min(1f, sum / norm);
    }
}
