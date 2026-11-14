package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;

/** The brightness of a custom sky with {@link SkyDesign.Light#DAY_NIGHT}, from the vanilla sky formula, the same on every version. */
public final class SkyLight {
    /** The least brightness, so that a night sky keeps a trace of its colors. */
    private static final float NIGHT = 0.04f;
    /** The ticks of one Minecraft day. */
    static final double DAY_TICKS = 24000;

    private SkyLight() {}

    /**
     * The factor for the sky and fog colors: 1 at noon in clear weather, low at night and in rain or thunder.
     *
     * @param dayTime the time of day in ticks, where 0 is sunrise and 6000 is noon
     * @param rain the rain level from 0 to 1
     * @param thunder the thunder level from 0 to 1
     */
    public static float brightness(double dayTime, float rain, float thunder) {
        float day = (float) Math.max(0, Math.min(1, Math.cos(sunAngle(dayTime) * 2 * Math.PI) * 2.0 + 0.5));
        float weather = (1 - 0.3f * rain) * (1 - 0.6f * thunder);
        return Math.max(NIGHT, day * weather);
    }

    /**
     * The vanilla angle of the sun in turns from noon, from 0 to 1, which moves slower near noon and faster near midnight.
     *
     * @param dayTime the time of day in ticks, where 0 is sunrise and 6000 is noon
     */
    static double sunAngle(double dayTime) {
        double d = daysFromNoon(dayTime);
        d -= Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (d * 2.0 + e) / 3.0;
    }

    /**
     * The turns of the vanilla sun since midnight with no wrap, so that a turning of one turn each day goes with the sun
     * and shows the design as it is at midnight.
     */
    static double sunTurns(double dayTime) {
        return Math.floor(daysFromNoon(dayTime)) + sunAngle(dayTime) - 0.5;
    }

    private static double daysFromNoon(double dayTime) {
        return dayTime / DAY_TICKS - 0.25;
    }

    /** The factor for a design: {@link #brightness} for {@link SkyDesign.Light#DAY_NIGHT}, otherwise 1. */
    public static float of(SkyDesign design, double dayTime, float rain, float thunder) {
        return design.light() == SkyDesign.Light.DAY_NIGHT ? brightness(dayTime, rain, thunder) : 1f;
    }
}
