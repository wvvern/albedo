package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyPreviewGeometryTest {
    private final Rect strip = new Rect(10, 20, 360, 105);
    private final SkyDesign horizon = SkyDesign.NONE, sphere = SkyDesign.NONE.withExtent(SkyDesign.Extent.SPHERE);

    @Test
    void northIsAtTheLeftAndEastAQuarterToTheRight() {
        assertEquals(10, SkyPreviewGeometry.x(strip, 0));
        assertEquals(100, SkyPreviewGeometry.x(strip, SkyPreviewGeometry.SUNRISE));
        assertEquals(280, SkyPreviewGeometry.x(strip, SkyPreviewGeometry.SUNSET));
        assertEquals(SkyPreviewGeometry.x(strip, 10), SkyPreviewGeometry.x(strip, 370));
    }

    @Test
    void theZenithIsTheTopAndTheHorizonIsFifteenDegreesAboveTheBottom() {
        assertEquals(20, SkyPreviewGeometry.y(strip, horizon, 90));
        assertEquals(20 + 90, SkyPreviewGeometry.y(strip, horizon, 0));
        assertEquals(20 + 104, SkyPreviewGeometry.y(strip, horizon, -60), "below the strip: the last row");
        assertEquals(105, SkyPreviewGeometry.rows(horizon, 180), "the strip shows the top 105 of 180 degrees");
    }

    @Test
    void aFullSphereShowsTheHorizonInTheMiddle() {
        Rect tall = new Rect(0, 0, 360, 180);
        assertEquals(90, SkyPreviewGeometry.y(tall, sphere, 0));
        assertEquals(179, SkyPreviewGeometry.y(tall, sphere, -90));
        assertEquals(256, SkyPreviewGeometry.rows(sphere, 256));
    }
}
