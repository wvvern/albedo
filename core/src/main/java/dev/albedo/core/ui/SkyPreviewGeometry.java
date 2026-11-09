package dev.albedo.core.ui;

import dev.albedo.core.sky.SkyImage;

/** Where a sky direction is on the preview strip, which shows all of the sky image: north at the left, east to the right, the zenith at the top. */
final class SkyPreviewGeometry {
    /** The compass labels and their azimuths in degrees. */
    static final String[] COMPASS = {"N", "E", "S", "W"};
    /** The azimuths of the sunrise and the sunset, where the vanilla sun path crosses the horizon on its way through the zenith. */
    static final int SUNRISE = 90, SUNSET = 270;

    private SkyPreviewGeometry() {}

    /** The x of an azimuth in degrees east of north. */
    static int x(Rect strip, double azimuth) {
        double a = ((azimuth % 360) + 360) % 360;
        return strip.x + (int) Math.round(a / 360 * strip.w);
    }

    /** The y of an elevation in degrees, inside the strip. */
    static int y(Rect strip, double elevation) {
        return strip.y + Math.min(strip.h - 1, (int) Math.round(SkyImage.v(elevation) * strip.h));
    }
}
