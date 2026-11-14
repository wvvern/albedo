package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import org.junit.jupiter.api.Test;

import static dev.albedo.core.sky.SkyFixtures.SUN;
import static dev.albedo.core.sky.SkyFixtures.ring;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyImageTest {
    @Test
    void theFogTakesTheColorOfTheDesignPartAtTheHorizon() {
        // An image that is black at the top and red at the bottom, with a black ring, and a red that shows the elevation.
        int w = 64, h = 32;
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xFF000000 | (i / w * 8) << 16;
        SkyImage img = new SkyImage(argb, w, h, SkyDesign.NONE, ring(), null, 1, 1, new short[4]);
        assertEquals(0, img.fogRgb(SkyTurn.STILL, -90, 1)[0], 1e-6f);
        // A quarter turn around the north pole on the horizon puts the bottom of the design at the east horizon.
        float east = img.fogRgb(SkyTurn.of(SUN, 0.25), -90, 1)[0];
        assertEquals(248 / 255f, east, 0.01f, "the fog has the color of the last row, near the nadir of the design");
        // Around a pole at the zenith, the camera to the south sees the design part from the east, near the horizon.
        float south = img.fogRgb(SkyTurn.of(SUN.withPoleHeight(90), 0.25), 0, 1)[0];
        assertEquals(0.5f, south, 0.05f);
    }

    @Test
    void theNorthFogColorIsNearTheFirstSectorOfTheHorizonColors() {
        SkyImage img = SkyGenerator.bake(SkyPresets.DUSK, 64, 32);
        float[] fog = img.fogRgb(SkyTurn.STILL, 180, 1);
        float north = img.program().block()[SkyProgram.RING];
        assertTrue(north > 0, "the horizon colors follow the layers");
        assertTrue(Math.abs(Oklab.toSrgb(north) / 255f - fog[0]) < 0.05f, "the north fog color is near the first sector");
    }
}
