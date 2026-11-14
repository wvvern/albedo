package dev.albedo.core.sky;

import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static dev.albedo.core.sky.SkyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GalaxyTest {
    @Test
    void galaxyDustTakesAwayLightAndJetsAddLightAlongTheAxis() {
        int w = 512, h = 256;
        // A tilted disc in the north, 30 degrees up, with its axis vertical.
        SkyLayer galaxy = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withTransform(new Transform(0, 30, 0, 200)).withParam(SkyLayer.Param.TILT, 60);
        SkyLayer clean = galaxy.withParam(SkyLayer.Param.DUST, 0).withParam(SkyLayer.Param.JETS, 0);
        int[] plain = SkyGenerator.bake(horizon(clean), w, h).argb();
        int[] dusty = SkyGenerator.bake(horizon(clean.withParam(SkyLayer.Param.DUST, 100)), w, h).argb();
        int[] jets = SkyGenerator.bake(horizon(clean.withParam(SkyLayer.Param.JETS, 100)), w, h).argb();
        long lightPlain = 0, lightDusty = 0;
        int darkest = 0;
        for (int i = 0; i < plain.length; i++) {
            assertTrue(red(dusty[i]) <= red(plain[i]) + 1, "dust only absorbs, pixel " + i);
            darkest = Math.max(darkest, red(plain[i]) - red(dusty[i]));
            if (red(plain[i]) < 40) continue;
            lightPlain += red(plain[i]);
            lightDusty += red(dusty[i]);
        }
        assertTrue(lightDusty < lightPlain * 0.97, "the dust takes light from the galaxy: " + lightDusty + " of " + lightPlain);
        assertTrue(darkest > 40, "the dust lanes are dark: " + darkest);
        // On the axis, 20 degrees over the center, the jet adds light, where the jet bends a little.
        int y = row(50, h), added = 0;
        for (int dx = -4; dx <= 4; dx++) {
            int i = y * w + Math.floorMod(dx, w);
            added = Math.max(added, blue(jets[i]) - blue(plain[i]));
        }
        assertTrue(added > 20, "the jet adds " + added);
    }

    @Test
    void aGalaxyWithNoDetailAndNoMotionKeepsThePixelsOfASkyFromBeforeThem() {
        SkyLayer galaxy = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withTransform(new Transform(0, 30, 0, 200));
        assertEquals(0, galaxy.param(SkyLayer.Param.DETAIL) + galaxy.param(SkyLayer.Param.MOTION), "a new galaxy is plain and still");
        assertEquals(-1082905965, Arrays.hashCode(SkyGenerator.bake(design(galaxy), 512, 256).argb()));
    }

    @Test
    void galaxyDetailAddsItsPartsAndItsDustStillOnlyAbsorbs() {
        int w = 512, h = 256;
        SkyLayer plain = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withTransform(new Transform(0, 30, 0, 200)).withParam(SkyLayer.Param.TILT, 60)
                .withParam(SkyLayer.Param.JETS, 0);
        SkyLayer fine = plain.withParam(SkyLayer.Param.DETAIL, 100);
        int[] before = SkyGenerator.bake(horizon(plain), w, h).argb(), after = SkyGenerator.bake(horizon(fine), w, h).argb();
        int changed = 0;
        for (int i = 0; i < before.length; i++) if (Math.abs(red(before[i]) - red(after[i])) > 2) changed++;
        assertTrue(changed > 1500, "the detail changes the disc and the halo: " + changed + " pixels");
        int[] clean = SkyGenerator.bake(horizon(fine.withParam(SkyLayer.Param.DUST, 0)), w, h).argb();
        int[] dusty = SkyGenerator.bake(horizon(fine.withParam(SkyLayer.Param.DUST, 100)), w, h).argb();
        for (int i = 0; i < clean.length; i++) {
            assertTrue(red(dusty[i]) <= red(clean[i]) + 1, "the dust gives back less red light than it takes, pixel " + i);
            assertTrue(blue(dusty[i]) <= blue(clean[i]) + 1, "and less blue light, pixel " + i);
        }
    }

    /** The linear light of a galaxy layer at a point of its image plane in galaxy units, at a time of the sky clock. */
    private static float[] galaxyLight(SkyLayer galaxy, float x, float y, float time, float texel) {
        SkyGenerator.Layer l = new SkyGenerator.Layer(galaxy, program(design(galaxy)).block(), 0);
        float size = 0.22f * galaxy.transform().scale() / 100f;
        float px = x * size, py = y * size, len = (float) Math.sqrt(px * px + py * py + 1);
        float[] out = new float[4];
        Galaxy.sample(l, new float[] {px / len, py / len, -1 / len}, texel, time, out);
        return new float[] {out[0] * out[3], out[1] * out[3], out[2] * out[3]};
    }

    private static float[] galaxyLight(SkyLayer galaxy, float x, float y, float time) {
        return galaxyLight(galaxy, x, y, time, 0.0005f);
    }

    @Test
    void aGalaxyMovesOnlyWithMotionAndComesBackToItsStartInTheHourOfTheSkyClock() {
        SkyLayer still = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withTransform(Transform.IDENTITY).withParam(SkyLayer.Param.DETAIL, 70);
        SkyLayer moving = still.withParam(SkyLayer.Param.MOTION, 60);
        float most = 0, wrap = 0;
        // Points along the near jet and through the nucleus.
        for (int i = 0; i <= 60; i++) {
            float y = 0.02f * i;
            assertArrayEquals(galaxyLight(still, 0.01f, y, 0), galaxyLight(still, 0.01f, y, 1234), 0f, "a still galaxy at " + y);
            float[] start = galaxyLight(moving, 0.01f, y, 0), later = galaxyLight(moving, 0.01f, y, 7), hour = galaxyLight(moving, 0.01f, y, 3600);
            for (int c = 0; c < 3; c++) {
                most = Math.max(most, Math.abs(later[c] - start[c]));
                wrap = Math.max(wrap, Math.abs(hour[c] - start[c]));
            }
        }
        assertTrue(most > 0.02f, "the jet and the nucleus change in 7 seconds: " + most);
        assertTrue(wrap < 2e-3f, "the galaxy at the end of the hour is the galaxy at its start: " + wrap);
    }

    @Test
    void aNewStarExplodesInTheDiscOfAMovingGalaxyAndIsGoneBeforeTheNext() {
        SkyLayer moving = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withTransform(Transform.IDENTITY).withParam(SkyLayer.Param.JETS, 0)
                .withParam(SkyLayer.Param.MOTION, 100);
        // 80 slots of 45 seconds in the hour at a motion of 100, and wide pixels, so that the grid of points finds the star.
        float most = 0, gone = 0, texel = 0.004f;
        for (int i = -40; i <= 40; i++) {
            for (int j = -18; j <= 18; j++) {
                float x = 0.025f * i, y = 0.025f * j;
                float base = galaxyLight(moving, x, y, 0, texel)[2];
                most = Math.max(most, galaxyLight(moving, x, y, 0.6f, texel)[2] - base);
                // The nucleus flickers, so the points near it change with the time also with no new star.
                if (x * x + y * y > 0.09f) gone = Math.max(gone, Math.abs(galaxyLight(moving, x, y, 44.9f, texel)[2] - base));
            }
        }
        assertTrue(most > 0.05f, "a new star shows in the first second of its slot: " + most);
        assertTrue(gone < 2e-3f, "and no light of it is left at the end of the slot: " + gone);
    }

    @Test
    void theShaderHasTheSameGalaxyNumbers() throws IOException {
        String glsl = skyLayersSource();
        String[] numbers = {
            "float ALBEDO_GALAXY_REACH = " + Galaxy.REACH,
            "float ALBEDO_GALAXY_JET_LENGTH = " + Galaxy.JET_LENGTH,
            "float ALBEDO_GALAXY_KNOT_CELL = " + Galaxy.KNOT_CELL,
            "vec3 ALBEDO_GALAXY_DUST_REDDENING = " + vec3(Galaxy.DUST_REDDENING),
            "vec3 ALBEDO_GALAXY_HALPHA = " + vec3(Galaxy.HALPHA),
            "vec3 ALBEDO_GALAXY_LOBE = " + vec3(Galaxy.LOBE),
            "vec3 ALBEDO_GALAXY_NUCLEUS = " + vec3(Galaxy.NUCLEUS),
            "float ALBEDO_GALAXY_DUST_THREADS = " + Galaxy.DUST_THREADS,
            "float ALBEDO_GALAXY_GAS_CELL = " + Galaxy.GAS_CELL,
            "float ALBEDO_GALAXY_CLUSTER_CELL = " + Galaxy.CLUSTER_CELL,
            "float ALBEDO_GALAXY_LOOP = " + Galaxy.LOOP,
            "vec3 ALBEDO_GALAXY_JET_END = " + vec3(Galaxy.JET_END),
            "vec3 ALBEDO_GALAXY_LOBE_RIM = " + vec3(Galaxy.LOBE_RIM),
            "vec3 ALBEDO_GALAXY_HOT_SPOT = " + vec3(Galaxy.HOT_SPOT),
            "vec3 ALBEDO_GALAXY_BOW_SHOCK = " + vec3(Galaxy.BOW_SHOCK),
            "vec3 ALBEDO_GALAXY_ION_CONE = " + vec3(Galaxy.ION_CONE),
            "vec3 ALBEDO_GALAXY_GAS_CORE = " + vec3(Galaxy.GAS_CORE),
            "vec3 ALBEDO_GALAXY_BULGE_CORE = " + vec3(Galaxy.BULGE_CORE),
            "vec3 ALBEDO_GALAXY_BULGE_EDGE = " + vec3(Galaxy.BULGE_EDGE),
            "vec3 ALBEDO_GALAXY_DUST_GLOW = " + vec3(Galaxy.DUST_GLOW),
            "vec3 ALBEDO_GALAXY_STREAM = " + vec3(Galaxy.STREAM),
            "vec3 ALBEDO_GALAXY_SUPERNOVA = " + vec3(Galaxy.SUPERNOVA),
            "float ALBEDO_GALAXY_STREAM_TURN = " + Galaxy.STREAM_TURN,
            "float ALBEDO_GALAXY_STREAM_HEAD = " + Galaxy.STREAM_HEAD,
            "float ALBEDO_GALAXY_STREAM_LONG = " + Galaxy.STREAM_LONG};
        for (String n : numbers) assertTrue(glsl.contains("const " + n + ";\n"), n);
    }
}
