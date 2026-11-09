package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.sky.SkyImage;

/**
 * Where a sky direction is on the preview strip, which shows the sky from the zenith at the top down to {@link #bottom}, with
 * north at the left and east to the right.
 */
final class SkyPreviewGeometry {
    /** The compass labels and their azimuths in degrees. */
    static final String[] COMPASS = {"N", "E", "S", "W"};
    /** The azimuths of the sunrise and the sunset, where the vanilla sun path crosses the horizon on its way through the zenith. */
    static final int SUNRISE = 90, SUNSET = 270;
    /** The lowest elevation in degrees that the strip shows when the sky stops at the horizon. */
    static final float HORIZON_BOTTOM = -15f;

    private SkyPreviewGeometry() {}

    /** The lowest elevation in degrees that the strip shows for a design. */
    static float bottom(SkyDesign design) {
        return design.extent() == SkyDesign.Extent.SPHERE ? SkyImage.BOTTOM : HORIZON_BOTTOM;
    }

    /** The number of rows from the top of a sky image of {@code height} rows that the strip shows for a design. */
    static int rows(SkyDesign design, int height) {
        float part = (SkyImage.TOP - bottom(design)) / (SkyImage.TOP - SkyImage.BOTTOM);
        return Math.max(1, Math.min(height, Math.round(part * height)));
    }

    /** The x of an azimuth in degrees east of north. */
    static int x(Rect strip, double azimuth) {
        double a = ((azimuth % 360) + 360) % 360;
        return strip.x + (int) Math.round(a / 360 * strip.w);
    }

    /** The y of an elevation in degrees, inside the strip. */
    static int y(Rect strip, SkyDesign design, double elevation) {
        double v = (SkyImage.TOP - elevation) / (SkyImage.TOP - bottom(design));
        return strip.y + Math.max(0, Math.min(strip.h - 1, (int) Math.round(v * strip.h)));
    }
}
