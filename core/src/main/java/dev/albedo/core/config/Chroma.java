package dev.albedo.core.config;

import java.util.Objects;

/** The Chroma effect of a look as an immutable value: a rainbow color for each kind of texture, which can go around the colors. */
public final class Chroma {
    public static final int DEFAULT_SPEED = 30, DEFAULT_SATURATION = 50, DEFAULT_BRIGHTNESS = 50;
    /** The highest speed, and the last index of the array that {@link #turns} fills. */
    public static final int MAX_SPEED = 100;
    /** The floats of the turns block of a GPU pass: one for each speed from 0 to {@link #MAX_SPEED}, in whole vec4 values. */
    public static final int TURN_FLOATS = (MAX_SPEED / 4 + 1) * 4;
    /** The time of one turn around the colors at speed 1 and at speed 100, in milliseconds. */
    static final long SLOWEST_PERIOD_MS = 120_000, FASTEST_PERIOD_MS = 10_000;
    public static final Chroma OFF = new Chroma(0, DEFAULT_SPEED, DEFAULT_SATURATION, DEFAULT_BRIGHTNESS);

    private final int strength, speed, saturation, brightness;

    /**
     * @param strength percent of the rainbow color, where 0 is off
     * @param speed 0 for still colors, else from 1 (one turn in 120 s) to 100 (one turn in 10 s)
     * @param saturation percent, where 50 is the default strength of the colors
     * @param brightness percent, where 50 is the default lightness of the colors
     */
    public Chroma(int strength, int speed, int saturation, int brightness) {
        this.strength = strength;
        this.speed = speed;
        this.saturation = saturation;
        this.brightness = brightness;
    }

    /** Chroma at {@code strength} percent with the default speed, saturation and brightness. */
    public static Chroma of(int strength) {
        return OFF.withStrength(strength);
    }

    public int strength() { return strength; }
    public int speed() { return speed; }
    public int saturation() { return saturation; }
    public int brightness() { return brightness; }

    public boolean isOn() { return strength > 0; }

    /** Whether the colors go around over time. */
    public boolean moves() { return strength > 0 && speed > 0; }

    public Chroma withStrength(int s) { return new Chroma(s, speed, saturation, brightness); }

    public Chroma withOptions(int newSpeed, int newSaturation, int newBrightness) {
        return new Chroma(strength, newSpeed, newSaturation, newBrightness);
    }

    /** The time in milliseconds of one turn around the colors, from 120 s at speed 1 down to 10 s at speed 100 on a log scale. */
    static double periodMs(int speed) {
        double t = (Math.max(1, Math.min(MAX_SPEED, speed)) - 1) / 99.0;
        return SLOWEST_PERIOD_MS * Math.pow((double) FASTEST_PERIOD_MS / SLOWEST_PERIOD_MS, t);
    }

    private static double turn(int speed, double seconds) {
        double turns = seconds * 1000 / periodMs(speed);
        return turns - Math.floor(turns);
    }

    /**
     * Puts the part of one turn at a time in seconds for each speed from 1 to {@link #MAX_SPEED} at the index of the
     * speed, so that a GPU pass finds the turn of each texel from its speed.
     */
    public static void turns(double seconds, float[] out) {
        out[0] = 0;
        for (int s = 1; s <= MAX_SPEED && s < out.length; s++) out[s] = (float) turn(s, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Chroma)) return false;
        Chroma c = (Chroma) o;
        return strength == c.strength && speed == c.speed && saturation == c.saturation && brightness == c.brightness;
    }

    @Override
    public int hashCode() { return Objects.hash(strength, speed, saturation, brightness); }

    @Override
    public String toString() { return "Chroma[" + strength + ", speed " + speed + ", saturation " + saturation + ", brightness " + brightness + "]"; }
}
