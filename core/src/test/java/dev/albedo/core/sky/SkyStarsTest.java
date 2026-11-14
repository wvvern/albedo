package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkyStarsTest {
    private static final float DEGREE = (float) (Math.PI / 180);

    /** Stars that do not twinkle and have no trails. */
    private static SkyStars stars(float amount, float spikes, float scale, int seed) {
        return stars(amount, spikes, scale, seed, 0, 0, new float[] {0, 1, 0});
    }

    private static SkyStars stars(float amount, float spikes, float scale, int seed, float twinkle, float time, float[] up) {
        return stars(amount, spikes, scale, seed, twinkle, time, up, 0, 0, new float[] {0, 1, 0});
    }

    /** Stars from the block of a program with one stars layer that holds these numbers. */
    private static SkyStars stars(float amount, float spikes, float scale, int seed, float twinkle, float time, float[] up, float trail,
            float trailLight, float[] axis) {
        float[] block = new float[SkyProgram.FLOATS];
        block[SkyProgram.at(0, SkyProgram.AMOUNT)] = amount;
        block[SkyProgram.at(0, SkyProgram.HEIGHT)] = spikes / 2;
        block[SkyProgram.at(0, SkyProgram.SCALE)] = scale;
        block[SkyProgram.at(0, SkyProgram.SEED)] = seed;
        block[SkyProgram.at(0, SkyProgram.TWINKLE)] = twinkle;
        block[SkyProgram.at(0, SkyProgram.TRAIL)] = trail;
        block[SkyProgram.at(0, SkyProgram.TRAIL_LIGHT)] = trailLight;
        for (int c = 0; c < 3; c++) block[SkyProgram.at(0, SkyProgram.TRAIL_AXIS + c * 4)] = axis[c];
        return SkyStars.of(block, 0, up, time);
    }

    /** The light at {@code p} of all stars of all cells up to three cells away, with no limit on the search. */
    private static float[] allStars(SkyStars stars, float scale, float[] p, float footprint) {
        float[] light = new float[3], star = new float[SkyStars.STAR_FLOATS], work = new float[SkyStars.WORK_FLOATS];
        for (SkyStars.Tier t : SkyStars.Tier.values()) {
            float n = t.cells / scale;
            int cx = (int) Math.floor(p[0] * n), cy = (int) Math.floor(p[1] * n), cz = (int) Math.floor(p[2] * n);
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    for (int k = -3; k <= 3; k++) if (stars.star(t, cx + i, cy + j, cz + k, star)) stars.add(t, star, p, footprint, work, light);
                }
            }
        }
        return light;
    }

    @Test
    void noStarIsCutAtACellBoundary() {
        int checked = 0, lit = 0;
        for (float scale : new float[] {0.5f, 1f, 2.5f}) {
            SkyStars stars = stars(1f, 2f, scale, 4);
            float[] star = new float[SkyStars.STAR_FLOATS];
            // Points all around the brightest stars of many large cells, where the halos and the spikes reach the farthest.
            for (int cell = 0; cell < 4000; cell++) {
                int hx = cell % 20 - 10, hy = cell / 20 % 20 - 10, hz = (int) (SkyStars.Tier.BRIGHT.cells / scale) - cell / 400;
                if (!stars.star(SkyStars.Tier.BRIGHT, hx, hy, hz, star) || star[3] < 8) continue;
                float reach = 0.5f * scale / SkyStars.Tier.BRIGHT.cells;
                for (int a = 0; a < 48; a++) {
                    float angle = a * (float) Math.PI / 24, r = reach * (a % 8 + 0.5f) / 8;
                    float[] p = offset(star, r * (float) Math.cos(angle), r * (float) Math.sin(angle));
                    for (float footprint : new float[] {0.003f * DEGREE, 0.05f * DEGREE, 0.7f * DEGREE}) {
                        float[] search = new float[3];
                        stars.light(p, footprint, search);
                        float[] all = allStars(stars, scale, p, footprint);
                        for (int c = 0; c < 3; c++) assertEquals(all[c], search[c], 1e-5f + all[c] * 1e-5f, "scale " + scale + " cell " + cell);
                        checked++;
                        if (all[0] > 0.001f) lit++;
                    }
                }
            }
        }
        assertTrue(checked > 3000, "checked " + checked);
        assertTrue(lit > checked / 3, "many points get light from the star: " + lit + " of " + checked);
    }

    /** A unit direction near the star direction {@code s}, moved by two angles across it. */
    private static float[] offset(float[] s, float a, float b) {
        float ex = s[2], ez = -s[0], el = (float) Math.sqrt(ex * ex + ez * ez);
        float nx = s[1] * ez / el, ny = (s[2] * ex - s[0] * ez) / el, nz = -s[1] * ex / el;
        float x = s[0] + a * ex / el + b * nx, y = s[1] + b * ny, z = s[2] + a * ez / el + b * nz;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / len, y / len, z / len};
    }

    @Test
    void manyStarsAreFaintAndFewAreBright() {
        SkyStars stars = stars(0.5f, 1f, 1f, 1);
        float[] star = new float[SkyStars.STAR_FLOATS];
        List<Float> field = new ArrayList<Float>(), bright = new ArrayList<Float>();
        // The stars of the two grids in the same cap of the sky, up to about 37 degrees from the z axis.
        for (int x = -56; x <= 56; x++) {
            for (int y = -56; y <= 56; y++) {
                for (int z = 69; z <= 88; z++) if (stars.star(SkyStars.Tier.FIELD, x, y, z, star) && star[2] >= 0.8f) field.add(star[3]);
            }
        }
        for (int x = -11; x <= 11; x++) {
            for (int y = -11; y <= 11; y++) {
                for (int z = 11; z <= 16; z++) if (stars.star(SkyStars.Tier.BRIGHT, x, y, z, star) && star[2] >= 0.8f) bright.add(star[3]);
            }
        }
        Collections.sort(field);
        Collections.sort(bright);
        assertTrue(field.size() > 20 * bright.size(), field.size() + " faint and " + bright.size() + " bright stars");
        assertTrue(field.get(field.size() / 2) < 0.12f, "most faint stars are near the faintest: " + field.get(field.size() / 2));
        int spiked = 0;
        for (float f : bright) if (f > 8) spiked++;
        assertTrue(spiked > 0 && spiked < bright.size() / 5, spiked + " of " + bright.size() + " bright stars are very bright");
    }

    @Test
    void aDiagonalOfTheGridHasAboutAsManyStarsAsAnAxis() {
        SkyStars stars = stars(1f, 1f, 1f, 2);
        int axis = capStars(stars, new float[] {0f, 0f, 1f}), diagonal = capStars(stars, new float[] {0.57735f, 0.57735f, 0.57735f});
        float ratio = diagonal / (float) axis;
        assertTrue(ratio > 0.8f && ratio < 1.1f, axis + " stars near the axis and " + diagonal + " near the diagonal");
    }

    /** The count of faint stars up to 12 degrees from the unit direction {@code d}. */
    private static int capStars(SkyStars stars, float[] d) {
        float n = SkyStars.Tier.FIELD.cells, cap = (float) Math.cos(12 * DEGREE);
        float[] star = new float[SkyStars.STAR_FLOATS];
        int r = (int) (n * 0.25f) + 2, count = 0;
        int cx = (int) Math.floor(d[0] * n), cy = (int) Math.floor(d[1] * n), cz = (int) Math.floor(d[2] * n);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (stars.star(SkyStars.Tier.FIELD, x, y, z, star) && star[0] * d[0] + star[1] * d[1] + star[2] * d[2] >= cap) count++;
                }
            }
        }
        return count;
    }

    @Test
    void theColorsGoFromBlueWhiteToOrangeWithSlightTints() {
        float[] c = new float[3];
        int blue = 0, red = 0;
        for (int i = 0; i < 1000; i++) {
            SkyStars.color((i + 0.5f) / 1000, c, 0);
            if (c[2] > c[0] * 1.1f) blue++;
            if (c[0] > c[2] * 1.3f) red++;
            for (float v : c) assertTrue(v >= 0.3f && v <= 1f, "a slight tint: " + v);
        }
        assertTrue(blue > 200 && blue < 450, blue + " blue stars of 1000");
        assertTrue(red > 200 && red < 450, red + " orange and red stars of 1000");
    }

    @Test
    void theStarsTwinkleGentlySlowlyAndMoreNearTheHorizon() {
        float[] up = {0, 1, 0}, star = new float[SkyStars.STAR_FLOATS], work = new float[SkyStars.WORK_FLOATS];
        SkyStars still = stars(1f, 1f, 1f, 3);
        float frame = 1 / 60f;
        double low = 0, high = 0;
        int lowCount = 0, highCount = 0;
        // The large cells of a ring near the horizon and of a ring high in the sky.
        for (int cell = 0; cell < 2 * 33 * 33 && lowCount + highCount < 80; cell++) {
            int hx = cell % 33 - 16, hz = cell / 33 % 33 - 16, hy = cell < 33 * 33 ? 2 : 13;
            if (hy == 2 && lowCount >= 40 || !still.star(SkyStars.Tier.BRIGHT, hx, hy, hz, star)) continue;
            float base = centerLight(still, star, work);
            float max = 0, deviation = 0;
            for (int i = 0; i < 300; i++) {
                SkyStars now = stars(1f, 1f, 1f, 3, 1f, i * frame * 4, up);
                SkyStars next = stars(1f, 1f, 1f, 3, 1f, (i * 4 + 1) * frame, up);
                now.star(SkyStars.Tier.BRIGHT, hx, hy, hz, star);
                float a = centerLight(now, star, work), b = centerLight(next, star, work);
                max = Math.max(max, Math.abs(b - a) / base);
                deviation += Math.abs(a - base) / base;
                assertTrue(Math.abs(a - base) / base < 0.45f, "a gentle twinkle: " + a + " of " + base);
            }
            assertTrue(max < 0.08f, "no star strobes from one frame to the next: " + max);
            if (star[1] < 0.2f) {
                low += deviation;
                lowCount++;
            } else if (star[1] > 0.8f) {
                high += deviation;
                highCount++;
            }
        }
        assertTrue(lowCount > 5 && highCount > 5, lowCount + " low and " + highCount + " high stars");
        assertTrue(low / lowCount > 1.5 * high / highCount, "more twinkle near the horizon: " + low / lowCount + " and " + high / highCount);
        SkyStars none = stars(1f, 1f, 1f, 3, 0f, 7f, up);
        assertEquals(centerLight(still, star, work), centerLight(none, star, work), "a twinkle of 0 keeps the stars still");
    }

    /** The green light of a star at its center. */
    private static float centerLight(SkyStars stars, float[] star, float[] work) {
        float[] light = new float[3], p = {star[0], star[1], star[2]};
        stars.add(SkyStars.Tier.BRIGHT, star, p, 0.0003f, work, light);
        return light[1];
    }

    @Test
    void theShaderHasTheSameTiers() throws IOException {
        String glsl = SkyFixtures.skyLayersSource();
        for (SkyStars.Tier t : SkyStars.Tier.values()) {
            String tier = "const AlbedoTier ALBEDO_TIER_" + t.name() + " = AlbedoTier(" + t.cells + ", " + t.minFlux + ", " + t.maxFlux + ", "
                    + t.slope + ", " + t.salt + ", " + t.detail + ");\n";
            assertTrue(glsl.contains(tier), tier);
            assertTrue(glsl.contains("albedoStarTier(p, footprint, ALBEDO_TIER_" + t.name() + ", "), t.name());
        }
    }

    @Test
    void theShaderHasTheSameNumbers() throws IOException {
        String glsl = SkyFixtures.skyLayersSource();
        String[] names = {"PIXEL", "HALO_LIGHT", "RIM_LIGHT", "SPIKE_FLUX", "SPIKE_LIGHT", "TINT", "TWINKLE_ZENITH", "TWINKLE_HORIZON",
            "TWINKLE_COLOR"};
        float[] values = {SkyStars.PIXEL, SkyStars.HALO_LIGHT, SkyStars.RIM_LIGHT, SkyStars.SPIKE_FLUX, SkyStars.SPIKE_LIGHT, SkyStars.TINT,
            SkyStars.TWINKLE_ZENITH, SkyStars.TWINKLE_HORIZON, SkyStars.TWINKLE_COLOR};
        for (int i = 0; i < names.length; i++) assertEquals(values[i], SkyFixtures.glslFloat(glsl, "ALBEDO_STAR_" + names[i]), names[i]);
        // The angles are degrees times the radians of one degree in both.
        String[] angles = {"CORE", "HALO", "RIM", "RIM_WIDTH", "SPIKE_LENGTH", "SPIKE_WIDTH"};
        float[] radians = {SkyStars.CORE, SkyStars.HALO, SkyStars.RIM, SkyStars.RIM_WIDTH, SkyStars.SPIKE_LENGTH, SkyStars.SPIKE_WIDTH};
        float[] degrees = {0.009f, 0.03f, 0.016f, 0.006f, 1.4f, 0.004f};
        for (int i = 0; i < angles.length; i++) {
            assertEquals(degrees[i] * SkyStars.RADIAN, radians[i], angles[i]);
            String line = "const float ALBEDO_STAR_" + angles[i] + " = " + degrees[i] + " * ALBEDO_PI / 180.0;\n";
            assertTrue(glsl.contains(line), line);
        }
        assertTrue(glsl.contains("const vec3 ALBEDO_STAR_RIM_WAVE = " + SkyFixtures.vec3(SkyStars.RIM_WAVE) + ";\n"));
        assertTrue(glsl.contains("const vec3 ALBEDO_STAR_SPIKE_WAVE = " + SkyFixtures.vec3(SkyStars.SPIKE_WAVE) + ";\n"));
    }

    /** A unit trail axis that is not on a grid axis, so that the arcs cross the cells at all angles. */
    private static final float[] AXIS = unit(0.3f, 0.8f, -0.5f);

    private static float[] unit(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / len, y / len, z / len};
    }

    /** The unit direction {@code v} turned by {@code angle} around {@link #AXIS}, which is the way back along a trail. */
    private static float[] back(float[] v, float angle) {
        double c = Math.cos(angle), s = Math.sin(angle), d = (AXIS[0] * v[0] + AXIS[1] * v[1] + AXIS[2] * v[2]) * (1 - c);
        return unit((float) (v[0] * c + (AXIS[1] * v[2] - AXIS[2] * v[1]) * s + AXIS[0] * d),
                (float) (v[1] * c + (AXIS[2] * v[0] - AXIS[0] * v[2]) * s + AXIS[1] * d),
                (float) (v[2] * c + (AXIS[0] * v[1] - AXIS[1] * v[0]) * s + AXIS[2] * d));
    }

    private static SkyStars trails(float scale, float degrees) {
        return stars(1f, 1f, scale, 6, 0, 0, new float[] {0, 1, 0}, degrees * DEGREE, 0.8f, AXIS);
    }

    @Test
    void theTrailSearchFindsTheTrailOfEachStarOnce() {
        int checked = 0, lit = 0;
        float[] star = new float[SkyStars.STAR_FLOATS], work = new float[SkyStars.WORK_FLOATS];
        for (float scale : new float[] {0.6f, 1f, 2f}) {
            SkyStars stars = trails(scale, 70);
            float n = SkyStars.Tier.BRIGHT.cells / scale;
            for (int cell = 0; cell < 400; cell++) {
                // The cells of directions on a spiral over the whole sphere.
                float y = 1 - (cell + 0.5f) / 200, r = (float) Math.sqrt(Math.max(0, 1 - y * y)), turn = cell * 2.3999632f;
                int hx = (int) Math.floor(r * Math.cos(turn) * n), hy = (int) Math.floor(y * n), hz = (int) Math.floor(r * Math.sin(turn) * n);
                if (!stars.star(SkyStars.Tier.BRIGHT, hx, hy, hz, star)) continue;
                float[] s = star.clone();
                for (int i = 0; i < 8; i++) {
                    // Points along the trail and a little across it, also near the head and past the end.
                    float angle = (i - 0.5f) * 10.5f * DEGREE, across = (i % 3 - 1) * 0.004f * DEGREE * (i + 1);
                    float[] on = back(s, angle);
                    float[] p = unit(on[0] + across * AXIS[0], on[1] + across * AXIS[1], on[2] + across * AXIS[2]);
                    for (float footprint : new float[] {0.02f * DEGREE, 0.08f * DEGREE, 0.4f * DEGREE}) {
                        float[] search = new float[3];
                        stars.trails(p, footprint, star, work, search);
                        float[] all = allTrails(stars, n, p, footprint);
                        for (int c = 0; c < 3; c++) assertEquals(all[c], search[c], 1e-6f + all[c] * 1e-5f, "scale " + scale + " cell " + cell);
                        checked++;
                        if (all[1] > 1e-3f) lit++;
                    }
                }
            }
        }
        assertTrue(checked > 3000, "checked " + checked);
        assertTrue(lit > checked / 4, "many points are on a trail: " + lit + " of " + checked);
    }

    /** The light at {@code p} of the trails of all stars of all large cells near the arc that goes 90 degrees back from it. */
    private static float[] allTrails(SkyStars stars, float n, float[] p, float footprint) {
        float[] light = new float[3], star = new float[SkyStars.STAR_FLOATS], work = new float[SkyStars.WORK_FLOATS];
        Set<Long> seen = new HashSet<Long>();
        for (int i = 0; i <= 180; i++) {
            // Each star is ahead of the points of its trail, so the stars of a point are on the arc forward from it.
            float[] q = back(p, -i * 0.5f * DEGREE);
            int cx = (int) Math.floor(q[0] * n), cy = (int) Math.floor(q[1] * n), cz = (int) Math.floor(q[2] * n);
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    for (int c = -2; c <= 2; c++) {
                        long key = ((long) (cx + a) * 4096 + cy + b) * 4096 + cz + c;
                        if (seen.add(key) && stars.star(SkyStars.Tier.BRIGHT, cx + a, cy + b, cz + c, star)) {
                            stars.addTrail(star, p, footprint, work, light);
                        }
                    }
                }
            }
        }
        return light;
    }

    @Test
    void aTrailGoesBackAlongTheTurnAndFadesToNothing() {
        SkyStars stars = trails(1f, 40);
        float[] s = unit(0.2f, 0.3f, 0.93f), work = new float[SkyStars.WORK_FLOATS];
        s = Arrays.copyOf(s, SkyStars.STAR_FLOATS);
        s[3] = 4;
        s[4] = 0.5f;
        float footprint = 0.07f * DEGREE;
        float[] light = new float[3];
        stars.addTrail(s, back(s, -2 * DEGREE), footprint, work, light);
        assertEquals(0, light[1], "no trail ahead of the star");
        float near = trailAt(stars, s, 2, footprint), middle = trailAt(stars, s, 20, footprint), end = trailAt(stars, s, 39.5f, footprint);
        assertTrue(near > 0.05f, "a bright trail behind the star: " + near);
        assertTrue(middle < near * 0.4f && middle > near * 0.1f, "it fades: " + near + " to " + middle);
        assertTrue(end < near * 0.002f, "and ends with no light: " + end);
        assertEquals(0, trailAt(stars, s, 41, footprint), "nothing past the end");
        // The trail keeps its light across it at all footprints, so it looks the same in the sharp and the baked sky.
        assertEquals(acrossSum(stars, s, 0.02f * DEGREE), acrossSum(stars, s, 0.2f * DEGREE), acrossSum(stars, s, 0.02f * DEGREE) * 0.02f);
    }

    /** The green light of the trail of {@code s} at some degrees back along it. */
    private static float trailAt(SkyStars stars, float[] s, float degrees, float footprint) {
        float[] light = new float[3];
        stars.addTrail(s, back(s, degrees * DEGREE), footprint, new float[SkyStars.WORK_FLOATS], light);
        return light[1];
    }

    /** The sum of the green light across the trail of {@code s} 10 degrees back, in steps much smaller than the trail width. */
    private static float acrossSum(SkyStars stars, float[] s, float footprint) {
        float[] on = back(s, 10 * DEGREE);
        float sum = 0, step = 0.001f * DEGREE;
        for (int i = -2000; i <= 2000; i++) {
            float[] light = new float[3];
            float[] p = unit(on[0] + i * step * AXIS[0], on[1] + i * step * AXIS[1], on[2] + i * step * AXIS[2]);
            stars.addTrail(s, p, footprint, new float[SkyStars.WORK_FLOATS], light);
            sum += light[1] * step;
        }
        return sum;
    }

    @Test
    void aStarNearTheAxisMakesAShortTightArcAndAFarStarALongOne() {
        SkyStars stars = trails(1f, 60);
        float footprint = 0.07f * DEGREE;
        float[] near = Arrays.copyOf(unit(AXIS[0] + 0.015f, AXIS[1], AXIS[2] - 0.01f), SkyStars.STAR_FLOATS);
        float[] far = Arrays.copyOf(unit(0.8f, -0.1f, 0.6f), SkyStars.STAR_FLOATS);
        near[3] = far[3] = 4;
        assertTrue(arcLength(stars, near, footprint) < 0.1f * arcLength(stars, far, footprint), "the arc is short near the pole");
        float faint = stars.spread(far), close = stars.spread(near);
        assertTrue(faint < 0.01f && close > 0.5f, "a faint star far from the axis spreads its light: " + faint + ", near: " + close);
    }

    /** The length in degrees of the lit part of a trail, from points along it. */
    private static float arcLength(SkyStars stars, float[] s, float footprint) {
        float lit = 0, h = s[0] * AXIS[0] + s[1] * AXIS[1] + s[2] * AXIS[2], radius = (float) Math.sqrt(1 - h * h);
        for (int i = 1; i < 600; i++) if (trailAt(stars, s, i * 0.1f, footprint) > 1e-4f) lit += 0.1f * radius;
        return lit;
    }

    @Test
    void theShaderHasTheSameTrails() throws IOException {
        String glsl = SkyFixtures.skyLayersSource();
        SkyStars.Tier t = SkyStars.Tier.BRIGHT;
        assertTrue(glsl.contains("albedoStarTrails(p, footprint, ALBEDO_TIER_" + t.name() + ", "));
        assertTrue(glsl.contains("ALBEDO_STAR_TRAIL_PIXEL = " + SkyStars.TRAIL_PIXEL + ";"));
        assertTrue(glsl.contains("ALBEDO_STAR_TRAIL_REACH = " + SkyStars.TRAIL_REACH + ";"));
        assertTrue(glsl.contains("ALBEDO_STAR_TRAIL_GAIN = " + SkyStars.TRAIL_GAIN + ";"));
        assertEquals(1.5f * DEGREE, SkyStars.FIELD_SPREAD, 1e-9f);
        assertTrue(glsl.contains("ALBEDO_STAR_FIELD_SPREAD = 1.5 * ALBEDO_PI / 180.0;"));
    }
}
