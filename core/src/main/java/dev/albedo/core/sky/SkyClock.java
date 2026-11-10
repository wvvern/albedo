package dev.albedo.core.sky;

/** The time of the sky layers that move, which each adapter writes into the sky block for each frame. */
public final class SkyClock {
    /** The time goes back to 0 after this many seconds, so that a float keeps a precision of about 0.25 ms. */
    static final long PERIOD_MS = 3_600_000L;

    /** The time that the harness holds still in seconds, or NaN when the clock runs. */
    private static volatile float held = Float.NaN;
    /** The nanoseconds that the clock is behind the system timer, so that it runs on from the time where it was held. */
    private static volatile long behindNanos;

    private SkyClock() {}

    /** The time in seconds, from 0 to {@link #PERIOD_MS} in seconds. */
    public static float seconds() {
        float h = held;
        return Float.isNaN(h) ? Math.floorMod(nanos() / 1_000_000L, PERIOD_MS) / 1000f : h;
    }

    /** The time in seconds with no wrap, for a slow move such as the turning sky, which a wrap would make jump. */
    public static double longSeconds() {
        float h = held;
        return Float.isNaN(h) ? nanos() / 1e9 : h;
    }

    /** Holds the clock still at a time in seconds, so that a screenshot of the moving layers is the same each time. */
    public static void hold(float seconds) {
        held = seconds;
    }

    /** Lets the clock run again after {@link #hold}, on from the held time. */
    public static void run() {
        float h = held;
        if (Float.isNaN(h)) return;
        behindNanos = System.nanoTime() - (long) (h * 1e9);
        held = Float.NaN;
    }

    private static long nanos() {
        return System.nanoTime() - behindNanos;
    }
}
