package dev.albedo.core.config;

import java.util.Objects;

/** The Chroma effect of a look as an immutable value: a rainbow color for each kind of texture, which can go around the colors. */
public final class Chroma {
    public static final int DEFAULT_SPEED = 30, DEFAULT_SATURATION = 50, DEFAULT_BRIGHTNESS = 50;
    /** The number of steps in one turn around the colors, so that a texture changes only 48 times in each period. */
    public static final int PHASES = 48;
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

    /** The time of one turn around the colors, from 120 s at speed 1 down to 10 s at speed 100 on a log scale. */
    public long periodMs() {
        double t = (Math.max(1, Math.min(100, speed)) - 1) / 99.0;
        return Math.round(SLOWEST_PERIOD_MS * Math.pow((double) FASTEST_PERIOD_MS / SLOWEST_PERIOD_MS, t));
    }

    /** The step from 0 to {@link #PHASES} - 1 of the colors at {@code timeMs}, which is always 0 when the colors do not move. */
    public int phase(long timeMs) {
        if (!moves()) return 0;
        long period = periodMs();
        return (int) (Math.floorMod(timeMs, period) * PHASES / period);
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
