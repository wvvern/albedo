package dev.albedo.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyPreviewGeometryTest {
    private final Rect strip = new Rect(10, 20, 360, 105);

    @Test
    void northIsAtTheLeftAndEastAQuarterToTheRight() {
        assertEquals(10, SkyPreviewGeometry.x(strip, 0));
        assertEquals(100, SkyPreviewGeometry.x(strip, SkyPreviewGeometry.SUNRISE));
        assertEquals(280, SkyPreviewGeometry.x(strip, SkyPreviewGeometry.SUNSET));
        assertEquals(SkyPreviewGeometry.x(strip, 10), SkyPreviewGeometry.x(strip, 370));
    }

    @Test
    void theZenithIsTheTopAndTheHorizonIsFifteenDegreesAboveTheBottom() {
        assertEquals(20, SkyPreviewGeometry.y(strip, 90));
        assertEquals(20 + 90, SkyPreviewGeometry.y(strip, 0));
        assertEquals(20 + 104, SkyPreviewGeometry.y(strip, -60), "below the image: the last row");
    }
}
