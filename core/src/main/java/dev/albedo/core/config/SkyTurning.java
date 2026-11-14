package dev.albedo.core.config;

/** How the whole custom sky turns around a pole over time, as the real night sky does; an amount of 0 keeps it still. */
public final class SkyTurning {
    /** The highest {@link #speed}: ten turns in one Minecraft day, which is one turn in two minutes. */
    public static final int MAX_SPEED = 10;
    /** The highest {@link #poleHeight}: the pole at the zenith. */
    public static final int MAX_POLE_HEIGHT = 90;
    /** The highest {@link #pole} azimuth in degrees. */
    public static final int MAX_POLE = 359;
    /** The longest {@link #trails} arc in degrees, which the real sky turns in six hours. */
    public static final int MAX_TRAILS = 90;
    /** The {@link #trailLight} of a design that does not set it. */
    public static final int TRAIL_LIGHT = 50;
    /** The {@link #poleHeight} of a new turning. */
    public static final int DEFAULT_POLE_HEIGHT = 45;
    /** The {@link #amount} at which the sky turns at its full {@link #speed}. */
    public static final int FULL_AMOUNT = 100;

    /** What time turns the sky. */
    public enum Clock implements FileKey {
        /** The day time of the world, so the sky turns with the sun and moon and stops when the daylight cycle stops. */
        WORLD,
        /** The real time, at a steady speed. */
        REAL
    }

    /** Which way the sky turns around the pole. */
    public enum Direction implements FileKey {
        /** The way the sun goes: up in the east and down in the west. */
        NORMAL,
        REVERSE
    }

    /** A sky that does not turn, which is also the turning that the bake reads when the sky has no trails. */
    public static final SkyTurning STILL = new SkyTurning(0, 1, Clock.WORLD, DEFAULT_POLE_HEIGHT, Direction.NORMAL, 0);
    /** The turning of a new custom sky: one slow turn in each Minecraft day of real time. */
    public static final SkyTurning DEFAULT = new SkyTurning(FULL_AMOUNT, 1, Clock.REAL, DEFAULT_POLE_HEIGHT, Direction.NORMAL, 0);

    private final int amount, speed, poleHeight, pole, trails, trailLight;
    private final Clock clock;
    private final Direction direction;

    /**
     * A turning with no star trails.
     *
     * @param amount the percent of {@code speed} at which the sky turns, from 0 to 100
     * @param speed the turns in one Minecraft day at an amount of 100, from 1 to {@link #MAX_SPEED}
     * @param poleHeight the elevation of the pole above the horizon in degrees, from 0 to {@link #MAX_POLE_HEIGHT}
     * @param pole the compass direction of the pole in degrees east of north, from 0 to {@link #MAX_POLE}
     */
    public SkyTurning(int amount, int speed, Clock clock, int poleHeight, Direction direction, int pole) {
        this(amount, speed, clock, poleHeight, direction, pole, 0, TRAIL_LIGHT);
    }

    /**
     * A turning with star trails.
     *
     * @param trails the arc in degrees of the trail behind each bright star, from 0 (no trails) to {@link #MAX_TRAILS}
     * @param trailLight the light of the trails in percent, from 0 to 100
     */
    public SkyTurning(int amount, int speed, Clock clock, int poleHeight, Direction direction, int pole, int trails, int trailLight) {
        this.amount = Range.clamp(amount, 0, FULL_AMOUNT);
        this.speed = Range.clamp(speed, 1, MAX_SPEED);
        this.clock = clock;
        this.poleHeight = Range.clamp(poleHeight, 0, MAX_POLE_HEIGHT);
        this.direction = direction;
        this.pole = Range.clamp(pole, 0, MAX_POLE);
        this.trails = Range.clamp(trails, 0, MAX_TRAILS);
        this.trailLight = Range.clamp(trailLight, 0, 100);
    }

    public int amount() { return amount; }

    public int speed() { return speed; }

    public Clock clock() { return clock; }

    public int poleHeight() { return poleHeight; }

    public Direction direction() { return direction; }

    public int pole() { return pole; }

    public int trails() { return trails; }

    public int trailLight() { return trailLight; }

    public boolean turns() { return amount > 0; }

    /** True when the bright stars draw trails, which only a sky that turns has. */
    public boolean showsTrails() { return turns() && trails > 0; }

    /** The part of the turning that the bake reads: the trails with their pole and direction, or {@link #STILL} with no trails. */
    public SkyTurning baked() {
        return showsTrails() ? new SkyTurning(FULL_AMOUNT, 1, Clock.WORLD, poleHeight, direction, pole, trails, trailLight) : STILL;
    }

    /** The turns of the sky in one Minecraft day. */
    public double turnsPerDay() { return speed * amount / (double) FULL_AMOUNT; }

    public SkyTurning withAmount(int a) { return new SkyTurning(a, speed, clock, poleHeight, direction, pole, trails, trailLight); }

    public SkyTurning withSpeed(int s) { return new SkyTurning(amount, s, clock, poleHeight, direction, pole, trails, trailLight); }

    public SkyTurning withClock(Clock c) { return new SkyTurning(amount, speed, c, poleHeight, direction, pole, trails, trailLight); }

    public SkyTurning withPoleHeight(int h) { return new SkyTurning(amount, speed, clock, h, direction, pole, trails, trailLight); }

    public SkyTurning withDirection(Direction d) { return new SkyTurning(amount, speed, clock, poleHeight, d, pole, trails, trailLight); }

    public SkyTurning withPole(int p) { return new SkyTurning(amount, speed, clock, poleHeight, direction, p, trails, trailLight); }

    public SkyTurning withTrails(int t) { return new SkyTurning(amount, speed, clock, poleHeight, direction, pole, t, trailLight); }

    public SkyTurning withTrailLight(int l) { return new SkyTurning(amount, speed, clock, poleHeight, direction, pole, trails, l); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SkyTurning)) return false;
        SkyTurning t = (SkyTurning) o;
        return t.amount == amount && t.speed == speed && t.clock == clock && t.poleHeight == poleHeight && t.direction == direction
                && t.pole == pole && t.trails == trails && t.trailLight == trailLight;
    }

    @Override
    public int hashCode() {
        int h = ((((amount * 31 + speed) * 31 + clock.ordinal()) * 31 + poleHeight) * 31 + direction.ordinal()) * 31 + pole;
        return (h * 31 + trails) * 31 + trailLight;
    }
}
