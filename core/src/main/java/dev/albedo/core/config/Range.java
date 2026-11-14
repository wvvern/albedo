package dev.albedo.core.config;

/** The int clamp of the config values, which Java 8 does not have. */
final class Range {
    private Range() {
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
