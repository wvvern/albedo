package dev.albedo.core.sky;

/** Float math of the bake and its Java twins. */
final class SkyMath {
    private SkyMath() {
    }

    /** The smoothstep of {@code t} from {@code a} to {@code b}, the same as {@code albedoSmooth} in the sky shader. */
    static float smooth(float a, float b, float t) {
        float x = clamp01((t - a) / (b - a));
        return x * x * (3 - 2 * x);
    }

    /** A sine wave from -1 to 1 at a number of turns, where only the part after the point counts, so that a large time keeps its precision. */
    static float wave(float turns) {
        return (float) Math.sin(2 * Math.PI * (turns - Math.floor(turns)));
    }

    /** A bell curve of height 1 at 0. */
    static float gauss(float v) {
        return (float) Math.exp(-v * v);
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
