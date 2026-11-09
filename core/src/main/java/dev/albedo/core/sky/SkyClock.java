package dev.albedo.core.sky;

/** The time of the moving sky layers, which each adapter gives to the sky shader as the uniform {@code albedoTime} in each frame. */
public final class SkyClock {
    /** The seconds after which the time starts again from 0, so that a float keeps steps much shorter than one frame. */
    static final double WRAP = 3600;

    private static final long START = System.nanoTime();

    private SkyClock() {}

    /** The seconds since the start of the game, from 0 up to {@link #WRAP}. */
    public static float seconds() {
        return (float) ((System.nanoTime() - START) / 1e9 % WRAP);
    }
}
