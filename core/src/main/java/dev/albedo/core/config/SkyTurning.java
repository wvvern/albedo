package dev.albedo.core.config;

import java.util.Locale;

/** How the whole custom sky turns around a pole over time, as the real night sky does; an amount of 0 keeps it still. */
public final class SkyTurning {
    /** The highest {@link #speed}: ten turns in one Minecraft day, which is one turn in two minutes. */
    public static final int MAX_SPEED = 10;
    /** The highest {@link #poleHeight}: the pole at the zenith. */
    public static final int MAX_POLE_HEIGHT = 90;
    /** The highest {@link #pole} azimuth in degrees. */
    public static final int MAX_POLE = 359;

    /** What time turns the sky. */
    public enum Clock {
        /** The day time of the world, so the sky turns with the sun and moon and stops when the daylight cycle stops. */
        WORLD,
        /** The real time, at a steady speed. */
        REAL;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Which way the sky turns around the pole. */
    public enum Direction {
        /** The way the sun goes: up in the east and down in the west. */
        NORMAL,
        REVERSE;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** A sky that does not turn, which is also the value of a design from before turning existed. */
    public static final SkyTurning NONE = new SkyTurning(0, 1, Clock.WORLD, 45, Direction.NORMAL, 0);

    private final int amount, speed, poleHeight, pole;
    private final Clock clock;
    private final Direction direction;

    /**
     * @param amount the percent of {@code speed} at which the sky turns, from 0 to 100
     * @param speed the turns in one Minecraft day at an amount of 100, from 1 to {@link #MAX_SPEED}
     * @param poleHeight the elevation of the pole above the horizon in degrees, from 0 to {@link #MAX_POLE_HEIGHT}
     * @param pole the compass direction of the pole in degrees east of north, from 0 to {@link #MAX_POLE}
     */
    public SkyTurning(int amount, int speed, Clock clock, int poleHeight, Direction direction, int pole) {
        this.amount = clamp(amount, 0, 100);
        this.speed = clamp(speed, 1, MAX_SPEED);
        this.clock = clock;
        this.poleHeight = clamp(poleHeight, 0, MAX_POLE_HEIGHT);
        this.direction = direction;
        this.pole = clamp(pole, 0, MAX_POLE);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public int amount() { return amount; }

    public int speed() { return speed; }

    public Clock clock() { return clock; }

    public int poleHeight() { return poleHeight; }

    public Direction direction() { return direction; }

    public int pole() { return pole; }

    public boolean turns() { return amount > 0; }

    /** The turns of the sky in one Minecraft day. */
    public double turnsPerDay() { return speed * amount / 100.0; }

    public SkyTurning withAmount(int a) { return new SkyTurning(a, speed, clock, poleHeight, direction, pole); }

    public SkyTurning withSpeed(int s) { return new SkyTurning(amount, s, clock, poleHeight, direction, pole); }

    public SkyTurning withClock(Clock c) { return new SkyTurning(amount, speed, c, poleHeight, direction, pole); }

    public SkyTurning withPoleHeight(int h) { return new SkyTurning(amount, speed, clock, h, direction, pole); }

    public SkyTurning withDirection(Direction d) { return new SkyTurning(amount, speed, clock, poleHeight, d, pole); }

    public SkyTurning withPole(int p) { return new SkyTurning(amount, speed, clock, poleHeight, direction, p); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SkyTurning)) return false;
        SkyTurning t = (SkyTurning) o;
        return t.amount == amount && t.speed == speed && t.clock == clock && t.poleHeight == poleHeight && t.direction == direction
                && t.pole == pole;
    }

    @Override
    public int hashCode() {
        return ((((amount * 31 + speed) * 31 + clock.ordinal()) * 31 + poleHeight) * 31 + direction.ordinal()) * 31 + pole;
    }
}
