package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AuroraTest {
    private static SkyProgram block(SkyLayer layer) {
        SkyDesign d = new SkyDesign(Collections.singletonList(layer), SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED,
                SkyDesign.Extent.HORIZON);
        return SkyProgram.of(d, new float[SkyImage.RING * 3]);
    }

    /** The light that the aurora adds at a time of the sky clock in the direction at an azimuth east of north and an elevation, in degrees. */
    private static float light(SkyProgram program, double azimuth, double elevation, float footprint, double time) {
        return lightAt(program.at(time, SkyTurn.STILL), azimuth, elevation, footprint);
    }

    /** The light that the aurora of a block adds in the direction at an azimuth east of north and an elevation, in degrees. */
    private static float lightAt(float[] block, double azimuth, double elevation, float footprint) {
        double az = Math.toRadians(azimuth), el = Math.toRadians(elevation);
        float[] p = {(float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (-Math.cos(el) * Math.cos(az))};
        float[] out = new float[4];
        Aurora.sample(block, 0, p, footprint, out);
        return out[1] * out[3];
    }

    @Test
    void theClockGoesOnAcrossTheWrapWithNoJump() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA));
        float[] before = b.at(0, SkyTurn.STILL).clone(), after = b.at(0, SkyTurn.STILL).clone();
        int clock = SkyProgram.at(0, SkyProgram.CLOCK);
        before[clock] = Aurora.PERIOD - 0.001f;
        after[clock] = 0.001f;
        float worst = 0, total = 0;
        for (double el = 2; el <= 40; el += 0.5) {
            for (double az = -60; az <= 60; az += 0.25) {
                float l = lightAt(before, az, el, 0.0015f);
                total += l;
                worst = Math.max(worst, Math.abs(lightAt(after, az, el, 0.0015f) - l));
            }
        }
        assertTrue(total > 1 && worst < 0.02f, "the largest change across the wrap is " + worst + " of a total " + total);
        assertEquals(3f, Aurora.clock(3, 0.5f), 1e-6f, "Speed 50 runs at the sky clock");
        assertEquals(1f, Aurora.clock(Aurora.PERIOD + 1, 0.5f), 1e-3f, "the clock wraps after a period");
    }

    @Test
    void theAuroraIsInTheNorthAboveTheHorizon() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA).withTransform(Transform.IDENTITY));
        float north = 0;
        for (int az = -60; az <= 60; az += 3) {
            for (int el = 0; el <= 30; el += 2) north = Math.max(north, light(b, az, el, 0.001f, 0));
        }
        assertTrue(north > 0.3f, "a bright curtain in the north: " + north);
        assertEquals(0f, light(b, 180, 20, 0.001f, 0), "no aurora in the south");
        assertEquals(0f, light(b, 0, -20, 0.001f, 0), "no aurora below the horizon");
    }

    @Test
    void theSkyUnderTheCurtainsIsOpenAndTheLowerEdgeIsTheBrightest() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA).withTransform(Transform.IDENTITY));
        float peak = 0, under = 0;
        int edges = 0, columns = 0;
        for (int az = -40; az <= 40; az += 2) {
            float best = 0, bestEl = 0;
            for (double el = 0; el <= 60; el += 0.25) {
                float l = light(b, az, el, 0.0005f, 0);
                if (l > best) {
                    best = l;
                    bestEl = (float) el;
                }
                if (el <= 3) under = Math.max(under, l);
            }
            peak = Math.max(peak, best);
            if (best < 0.05f) continue;
            columns++;
            // The brightest light of a column is near the lower edge of a curtain, with less light a few degrees higher.
            if (light(b, az, bestEl + 6, 0.0005f, 0) < 0.7f * best) edges++;
        }
        assertTrue(under < 0.05f * peak, "open night sky near the horizon: " + under + " of " + peak);
        assertTrue(columns > 10 && edges > columns * 0.7, edges + " of " + columns + " columns are brightest at a lower edge");
    }

    @Test
    void theShaderSearchesTheSameSteps() throws IOException {
        String glsl = SkyProgramTest.skyLayersSource();
        assertTrue(glsl.contains("#define ALBEDO_AURORA_STEPS " + Aurora.STEPS + "\n"));
    }

    @Test
    void theAuroraMovesWithTheTimeOnlyWithASpeed() {
        SkyLayer moving = SkyLayer.of(1, SkyLayer.Kind.AURORA);
        SkyProgram b = block(moving), still = block(moving.withParam(SkyLayer.Param.SPEED, 0));
        double change = 0, stillChange = 0, total = 0;
        for (int az = -40; az <= 40; az++) {
            change += Math.abs(light(b, az, 30, 0.001f, 20) - light(b, az, 30, 0.001f, 0));
            stillChange += Math.abs(light(still, az, 30, 0.001f, 20) - light(still, az, 30, 0.001f, 0));
            total += light(b, az, 30, 0.001f, 0);
        }
        assertTrue(total > 0.3 && change > 0.5 * total, "the curtains move in 20 s: " + change + " of " + total);
        assertEquals(0, stillChange, "speed 0 keeps the curtains still");
    }

    @Test
    void theCurtainsMoveWithoutJumpsWhereTheyFoldOrEnd() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA));
        for (float time : new float[] {40, 77}) {
            float worst = 0;
            for (double el = 2; el <= 40; el += 0.5) {
                for (double az = -60; az <= 60; az += 0.1) {
                    // In 0.5 ms the curtains move much less than a pixel, so a larger change is a jump of the light.
                    worst = Math.max(worst, Math.abs(light(b, az, el, 0.0015f, time + 0.0005) - light(b, az, el, 0.0015f, time)));
                }
            }
            assertTrue(worst < 0.005f, "the largest change in 0.5 ms after " + time + " s is " + worst);
        }
    }

    @Test
    void aWidePixelSeesNoFineRays() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA));
        double sharp = 0, wide = 0;
        float lastSharp = light(b, -30, 30, 0.0005f, 0), lastWide = light(b, -30, 30, 0.02f, 0);
        for (double az = -30; az <= 30; az += 0.05) {
            float s = light(b, az, 30, 0.0005f, 0), w = light(b, az, 30, 0.02f, 0);
            sharp += Math.abs(s - lastSharp);
            wide += Math.abs(w - lastWide);
            lastSharp = s;
            lastWide = w;
        }
        assertTrue(wide < sharp * 0.6, "the rays fade out where they are narrower than a pixel: " + wide + " and " + sharp);
    }
}
