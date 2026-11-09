package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AuroraTest {
    private static float[] block(SkyLayer layer) {
        SkyDesign d = new SkyDesign(Collections.singletonList(layer), SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED,
                SkyDesign.Extent.HORIZON);
        return SkyProgram.of(d, new float[SkyImage.RING * 3]).block();
    }

    /** The light that the aurora adds in the direction at an azimuth east of north and an elevation, in degrees. */
    private static float light(float[] block, double azimuth, double elevation, float footprint, float time) {
        double az = Math.toRadians(azimuth), el = Math.toRadians(elevation);
        float[] p = {(float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (-Math.cos(el) * Math.cos(az))};
        float[] out = new float[4];
        Aurora.sample(block, 0, p, footprint, time, out);
        return out[1] * out[3];
    }

    @Test
    void theAuroraIsInTheNorthAboveTheHorizon() {
        float[] b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA).withTransform(Transform.IDENTITY));
        float north = 0;
        for (int az = -60; az <= 60; az += 3) {
            for (int el = 0; el <= 30; el += 2) north = Math.max(north, light(b, az, el, 0.001f, 0));
        }
        assertTrue(north > 0.3f, "a bright curtain in the north: " + north);
        assertEquals(0f, light(b, 180, 20, 0.001f, 0), "no aurora in the south");
        assertEquals(0f, light(b, 0, -20, 0.001f, 0), "no aurora below the horizon");
    }

    @Test
    void theAuroraMovesWithTheTimeOnlyWithASpeed() {
        SkyLayer moving = SkyLayer.of(1, SkyLayer.Kind.AURORA);
        float[] b = block(moving), still = block(moving.withParam(SkyLayer.Param.SPEED, 0));
        double change = 0, stillChange = 0;
        for (int az = -40; az <= 40; az++) {
            change += Math.abs(light(b, az, 14, 0.001f, 20) - light(b, az, 14, 0.001f, 0));
            stillChange += Math.abs(light(still, az, 14, 0.001f, 20) - light(still, az, 14, 0.001f, 0));
        }
        assertTrue(change > 0.5, "the curtains move in 20 s: " + change);
        assertEquals(0, stillChange, "speed 0 keeps the curtains still");
    }

    @Test
    void aWidePixelSeesNoFineRays() {
        float[] b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA));
        double sharp = 0, wide = 0;
        float lastSharp = light(b, -30, 14, 0.0005f, 0), lastWide = light(b, -30, 14, 0.02f, 0);
        for (double az = -30; az <= 30; az += 0.05) {
            float s = light(b, az, 14, 0.0005f, 0), w = light(b, az, 14, 0.02f, 0);
            sharp += Math.abs(s - lastSharp);
            wide += Math.abs(w - lastWide);
            lastSharp = s;
            lastWide = w;
        }
        assertTrue(wide < sharp * 0.6, "the rays fade out where they are narrower than a pixel: " + wide + " and " + sharp);
    }
}
