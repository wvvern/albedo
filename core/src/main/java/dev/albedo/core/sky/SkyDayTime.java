package dev.albedo.core.sky;

/**
 * The day time of the world in each frame between two ticks, so that a sky that turns with the world clock moves in each
 * frame and not only 20 times a second, as vanilla moves an entity from its position in the last tick.
 */
public final class SkyDayTime {
    /** A larger step in one tick is a jump, for example from a time command, which shows at once. */
    static final long MAX_STEP = 100;

    private long previous;
    private long current;

    /** Records the day time of the world at the end of each client tick, which the frames until the next tick move to. */
    public void tick(long dayTime) {
        previous = current;
        current = dayTime;
    }

    /**
     * The day time in ticks of a frame, from the last tick but one to the last tick.
     *
     * @param partialTick the part of a tick from the last tick to this frame, from 0 to 1
     */
    public double at(float partialTick) {
        long step = current - previous;
        return step < 0 || step > MAX_STEP ? current : previous + step * (double) partialTick;
    }
}
