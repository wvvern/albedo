package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;

/** The brightness of a custom sky with {@link SkyDesign.Light#DAY_NIGHT}, from the vanilla sky formula, the same on every version. */
public final class SkyLight {
    /** The least brightness, so that a night sky keeps a trace of its colors. */
    private static final float NIGHT = 0.04f;

    private SkyLight() {}

    /**
     * The factor for the sky and fog colors: 1 at noon in clear weather, low at night and in rain or thunder.
     *
     * @param dayTime the time of day in ticks, where 0 is sunrise and 6000 is noon
     * @param rain the rain level from 0 to 1
     * @param thunder the thunder level from 0 to 1
     */
    public static float brightness(double dayTime, float rain, float thunder) {
        double d = dayTime / 24000.0 - 0.25;
        d -= Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        double angle = (d * 2.0 + e) / 3.0;
        float day = (float) Math.max(0, Math.min(1, Math.cos(angle * 2 * Math.PI) * 2.0 + 0.5));
        float weather = (1 - 0.3f * rain) * (1 - 0.6f * thunder);
        return Math.max(NIGHT, day * weather);
    }

    /** The factor for a design: {@link #brightness} for {@link SkyDesign.Light#DAY_NIGHT}, otherwise 1. */
    public static float of(SkyDesign design, double dayTime, float rain, float thunder) {
        return design.light() == SkyDesign.Light.DAY_NIGHT ? brightness(dayTime, rain, thunder) : 1f;
    }
}
