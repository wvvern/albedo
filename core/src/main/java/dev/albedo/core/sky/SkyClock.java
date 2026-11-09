package dev.albedo.core.sky;

/** The time of the sky layers that move, which each adapter writes into the sky block for each frame. */
public final class SkyClock {
    /** The time goes back to 0 after this many seconds, so that a float keeps a precision of about 0.25 ms. */
    public static final long PERIOD_MS = 3_600_000L;

    /** The time that the harness holds still in seconds, or NaN when the clock runs. */
    private static volatile float held = Float.NaN;

    private SkyClock() {}

    /** The time in seconds, from 0 to {@link #PERIOD_MS} in seconds. */
    public static float seconds() {
        float h = held;
        return Float.isNaN(h) ? Math.floorMod(System.nanoTime() / 1_000_000L, PERIOD_MS) / 1000f : h;
    }

    /** Holds the clock still at a time in seconds, so that a screenshot of the moving layers is the same each time. */
    public static void hold(float seconds) {
        held = seconds;
    }

    /** Lets the clock run again after {@link #hold}. */
    public static void run() {
        held = Float.NaN;
    }
}
