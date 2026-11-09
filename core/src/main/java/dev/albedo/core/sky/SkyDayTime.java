package dev.albedo.core.sky;

/**
 * The day time of the world in each frame between two ticks, so that a sky that turns with the world clock moves in each
 * frame and not only 20 times a second, as vanilla moves an entity from its position in the last tick.
 */
public final class SkyDayTime {
    /** A larger step in one tick is a jump, for example from a time command, which shows at once. */
    static final long MAX_STEP = 100;

    private static long previous;
    private static long current;
    /** The partial tick that the harness holds, or NaN when the frames move between the ticks. */
    private static float held = Float.NaN;

    private SkyDayTime() {}

    /** Records the day time of the world at the end of each client tick, which the frames until the next tick move to. */
    public static void tick(long dayTime) {
        if (!Float.isNaN(held)) return;
        previous = current;
        current = dayTime;
    }

    /**
     * The day time in ticks of a frame, from the last tick but one to the last tick.
     *
     * @param partialTick the part of a tick from the last tick to this frame, from 0 to 1
     */
    public static double at(float partialTick) {
        float h = held;
        double part = Float.isNaN(h) ? partialTick : h;
        long step = current - previous;
        return step < 0 || step > MAX_STEP ? current : previous + step * part;
    }

    /** Holds the partial tick and the two day times still, so that screenshots show frames between the same two ticks. */
    public static void hold(float partialTick) {
        held = partialTick;
    }

    /** Lets the frames move between the ticks again after {@link #hold}. */
    public static void run() {
        held = Float.NaN;
    }
}
