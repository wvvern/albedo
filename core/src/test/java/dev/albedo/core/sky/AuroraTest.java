package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static dev.albedo.core.sky.SkyFixtures.direction;
import static dev.albedo.core.sky.SkyFixtures.program;
import static dev.albedo.core.sky.SkyFixtures.single;
import static dev.albedo.core.sky.SkyFixtures.skyLayersSource;
import static org.junit.jupiter.api.Assertions.*;

class AuroraTest {
    /** Both curtains, the near one and the far one under it. */
    private static final int BOTH = 2;

    private static SkyProgram block(SkyLayer layer) {
        return program(single(layer, SkyDesign.Extent.HORIZON));
    }

    /** The light that the aurora adds at a time of the sky clock in the direction at an azimuth east of north and an elevation, in degrees. */
    private static float light(SkyProgram program, double azimuth, double elevation, float footprint, double time) {
        return lightAt(program.at(time, SkyTurn.STILL), azimuth, elevation, footprint, BOTH);
    }

    /** The light of the first {@code curtains} curtains of a block in the direction at an azimuth east of north and an elevation, in degrees. */
    private static float lightAt(float[] block, double azimuth, double elevation, float footprint, int curtains) {
        float[] out = new float[4];
        Aurora.sample(block, 0, direction(Math.toRadians(azimuth), Math.toRadians(elevation)), footprint, curtains, out);
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
                float l = lightAt(before, az, el, 0.0015f, BOTH);
                total += l;
                worst = Math.max(worst, Math.abs(lightAt(after, az, el, 0.0015f, BOTH) - l));
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

    /** The light of the near curtain alone, with no far curtain under it, in the direction at an azimuth and an elevation in degrees. */
    private static float nearLight(SkyProgram program, double azimuth, double elevation, float footprint, double time) {
        return lightAt(program.at(time, SkyTurn.STILL), azimuth, elevation, footprint, 1);
    }

    @Test
    void theLowerEdgeOfACurtainIsOneSmoothLineAcrossTheSky() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA).withTransform(Transform.IDENTITY));
        for (double time : new double[] {0, 40, 77}) {
            int columns = 0;
            double last = Double.NaN, worst = 0;
            float[] l = new float[600];
            // Steps of 0.05 degrees, because the soft edge moves more with the light of the column than a hard one would.
            for (double az = -60; az <= 60; az += 0.05) {
                float best = 0;
                for (int i = 0; i < l.length; i++) {
                    l[i] = nearLight(b, az, 1 + i * 0.1, 0.0015f, time);
                    best = Math.max(best, l[i]);
                }
                // A faint column is the end of the curtain or a gap.
                if (best < 0.15f) {
                    last = Double.NaN;
                    continue;
                }
                // A quarter of the brightest light, which is in the soft edge also where the rays above it are brighter than the band at it.
                int i = 0;
                while (l[i] <= 0.25f * best) i++;
                double edge = 1 + i * 0.1 - 0.1 * (l[i] - 0.25f * best) / Math.max(1e-6f, l[i] - (i > 0 ? l[i - 1] : 0));
                if (!Double.isNaN(last)) {
                    columns++;
                    worst = Math.max(worst, Math.abs(edge - last));
                }
                last = edge;
            }
            assertTrue(columns > 600 && worst < 0.3, "the largest step of the lower edge in 0.05 degrees of azimuth after " + time
                    + " s is " + worst + " degrees in " + columns + " columns");
        }
    }

    @Test
    void theFarCurtainIsAFainterArcUnderTheNearOne() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA).withTransform(Transform.IDENTITY));
        double far = 0, farHeight = 0, near = 0, nearHeight = 0;
        for (double az = -60; az <= 60; az += 0.5) {
            for (double el = 1; el <= 70; el += 0.25) {
                float n = nearLight(b, az, el, 0.001f, 0), f = light(b, az, el, 0.001f, 0) - n;
                near += n;
                nearHeight += n * el;
                far += f;
                farHeight += f * el;
            }
        }
        assertTrue(far > 0.01 * near && far < 0.4 * near, "the far curtain has less light: " + far + " of " + near);
        assertTrue(farHeight / far < nearHeight / near - 8, "and is lower in the sky: " + farHeight / far + " and " + nearHeight / near);
    }

    @Test
    void theLightOfACurtainRisesSmoothlyFromItsLowerEdge() {
        SkyProgram b = block(SkyLayer.of(1, SkyLayer.Kind.AURORA).withTransform(Transform.IDENTITY));
        float[] l = new float[2000];
        int columns = 0, soft = 0;
        double steepest = 0;
        for (int az = -40; az <= 40; az += 2) {
            float best = 0;
            int top = 0;
            for (int i = 0; i < l.length; i++) {
                // A fine pixel, so that the softness comes from the curtain and not from the width of the pixel.
                l[i] = light(b, az, i * 0.02, 0.0002f, 0);
                if (l[i] > best) {
                    best = l[i];
                    top = i;
                }
            }
            if (best < 0.1f) continue;
            columns++;
            int high = top, low;
            while (high > 0 && l[high] > 0.9f * best) high--;
            low = high;
            while (low > 0 && l[low] > 0.1f * best) low--;
            for (int i = low; i < high; i++) steepest = Math.max(steepest, (l[i + 1] - l[i]) / best);
            // From a tenth to nine tenths of the brightest light of the column over more than half a degree.
            if ((high - low) * 0.02 > 0.5) soft++;
        }
        assertTrue(columns > 10 && soft > columns * 0.8, soft + " of " + columns + " columns come in softly at the lower edge");
        assertTrue(steepest < 0.08, "the largest rise in 0.02 degrees under the brightest light is " + steepest + " of it");
    }

    @Test
    void theShaderSearchesTheSameSteps() throws IOException {
        String glsl = skyLayersSource();
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
        assertTrue(total > 0.3 && change > 0.3 * total, "the curtains move in 20 s: " + change + " of " + total);
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
