package dev.albedo.core.sky;

/** The time of the sky layers that move, which each adapter writes into the sky block for each frame. */
public final class SkyClock {
    /** The time goes back to 0 after this many ms (one hour), so that a float keeps a precision of about 0.25 ms. */
    static final long PERIOD_MS = 3_600_000L;

    private static volatile Phase phase = Phase.running(0L);

    private SkyClock() {}

    /** The time in seconds, wrapped every {@link #PERIOD_MS}. */
    public static float seconds() {
        Phase p = phase;
        return p.held ? p.heldSeconds : Math.floorMod(p.nanos() / 1_000_000L, PERIOD_MS) / 1000f;
    }

    /** The time in seconds with no wrap, for a slow move such as the turning sky, which a wrap would make jump. */
    public static double longSeconds() {
        Phase p = phase;
        return p.held ? p.heldSeconds : p.nanos() / 1e9;
    }

    /** A time of {@link #longSeconds} in seconds, wrapped every {@link #PERIOD_MS} in double precision. */
    static double wrap(double seconds) {
        double period = PERIOD_MS / 1000.0;
        return seconds - period * Math.floor(seconds / period);
    }

    /** Holds the clock still at a time in seconds, so that a screenshot of the moving layers is the same each time. */
    public static void hold(float seconds) {
        phase = Phase.held(seconds);
    }

    /** Lets the clock run again after {@link #hold}, on from the held time. */
    public static void run() {
        Phase p = phase;
        if (!p.held) return;
        phase = Phase.running(System.nanoTime() - (long) (p.heldSeconds * 1e9));
    }

    /** The clock runs behind the system timer, or the harness holds it still at a time. */
    private static final class Phase {
        final boolean held;
        /** The time that the harness holds still in seconds. */
        final float heldSeconds;
        /** The nanoseconds that the clock is behind the system timer, so that it runs on from the time where it was held. */
        final long behindNanos;

        private Phase(boolean held, float heldSeconds, long behindNanos) {
            this.held = held;
            this.heldSeconds = heldSeconds;
            this.behindNanos = behindNanos;
        }

        static Phase running(long behindNanos) {
            return new Phase(false, 0f, behindNanos);
        }

        static Phase held(float seconds) {
            return new Phase(true, seconds, 0L);
        }

        long nanos() {
            return System.nanoTime() - behindNanos;
        }
    }
}
