package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static dev.albedo.core.sky.SkyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SkyGeneratorTest {
    private static final int W = 128, H = 32;

    private static SkyDesign of(SkyLayer... layers) {
        return new SkyDesign(Arrays.asList(layers), SkyDesign.Celestial.SHOW, SkyDesign.Light.FIXED, SkyDesign.Extent.HORIZON,
                SkyDesign.Render.SHARP, SkyTurning.STILL);
    }

    private static SkyLayer layer(int id, SkyLayer.Kind kind) {
        return SkyLayer.of(id, kind);
    }

    @Test
    void theSameDesignGivesTheSamePixelsAndASeedChangesThem() {
        SkyDesign a = of(layer(1, SkyLayer.Kind.NOISE));
        SkyDesign b = of(layer(1, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.SEED, 2));
        assertArrayEquals(SkyGenerator.bake(a, W, H).argb(), SkyGenerator.bake(a, W, H).argb());
        assertFalse(Arrays.equals(SkyGenerator.bake(a, W, H).argb(), SkyGenerator.bake(b, W, H).argb()));
    }

    @Test
    void theCloudsPulseSmoothlyInMostRegions() {
        int still = 0, strong = 0;
        for (int i = 0; i < 400; i++) {
            float[] p = direction(i * 0.7, (i % 17) * 0.09 - 0.7);
            assertEquals(1f, SkyGenerator.pulse(0, 1, 7, p, 1, 5), "a pulse of 0 keeps the clouds still");
            float min = Float.MAX_VALUE, max = 0, step = 0, last = SkyGenerator.pulse(0.5f, 1, 7, p, 1, 0);
            for (int t = 0; t <= 60 * 30; t++) {
                float k = SkyGenerator.pulse(0.5f, 1, 7, p, 1, t / 60f);
                min = Math.min(min, k);
                max = Math.max(max, k);
                step = Math.max(step, Math.abs(k - last));
                last = k;
            }
            assertTrue(min >= 0.5f && max <= 2f, "a pulse of 50 percent from " + min + " to " + max);
            assertTrue(step < 0.03f, "a smooth pulse, with a step of " + step + " in a frame");
            float full = SkyGenerator.pulse(1, 1, 7, p, 1, 2);
            assertTrue(full > 0.09f && full < 11.1f, "a pulse of 100 percent of " + full);
            assertEquals(SkyGenerator.pulse(1, 1, 7, p, 1, 3), SkyGenerator.pulse(1, 1, 7, p, 1, 183), 1e-3f, "the waves repeat in 180 s");
            assertEquals(SkyGenerator.pulse(1, 1, 7, p, 1, 0), SkyGenerator.pulse(1, 1, 7, p, 1, 3600), 1e-3f, "no jump at the hour");
            if (max - min < 1e-6f) still++;
            else if (max / min > 1.5f) strong++;
        }
        assertTrue(still > 10 && strong > 120, still + " still and " + strong + " strong places");
    }

    @Test
    void aBrighterCloudKeepsItsHueAndStopsSmoothlyAtThePeak() {
        float[] pink = {1f, 0.16f, 0.43f};
        SkyGenerator.brighten(5, pink);
        assertArrayEquals(new float[] {1f, 0.16f, 0.43f}, pink, 2e-4f, "a peak of 1 cannot get brighter");
        float[] dim = {0.4f, 0.1f, 0.2f};
        SkyGenerator.brighten(5, dim);
        assertTrue(dim[0] < 1 && dim[0] > 0.9f, "the peak goes up to 1 but not over it: " + dim[0]);
        assertEquals(0.25f, dim[1] / dim[0], 1e-4f, "the hue stays");
        float[] small = {0.4f, 0.1f, 0.2f}, smaller = {0.4f, 0.1f, 0.2f};
        SkyGenerator.brighten(1.001f, small);
        SkyGenerator.brighten(0.999f, smaller);
        assertEquals(small[0] - 0.4f, 0.4f - smaller[0], 1e-5f, "the curve has no corner at a factor of 1");
        SkyGenerator.brighten(0.1f, smaller);
        assertTrue(smaller[0] > 0, "a darker cloud stays above black");
    }

    @Test
    void thePresetsKeepTheirPixels() {
        assertEquals(-1327994571, Arrays.hashCode(SkyGenerator.bake(SkyPresets.DUSK, 256, 128).argb()), "dusk");
        assertEquals(-1992624876, Arrays.hashCode(SkyGenerator.bake(SkyPresets.NEBULA, 256, 128).argb()), "nebula");
        assertEquals(766003272, Arrays.hashCode(SkyGenerator.bake(SkyPresets.GALAXY, 256, 128).argb()), "galaxy");
        assertEquals(-1617945180, Arrays.hashCode(SkyGenerator.bake(SkyPresets.METEORS, 256, 128).argb()), "meteors");
        assertEquals(-982631882, Arrays.hashCode(SkyGenerator.bake(SkyPresets.AURORA, 256, 128).argb()), "aurora");
        assertEquals(-1457549582, Arrays.hashCode(SkyGenerator.bake(SkyPresets.TRAILS, 256, 128).argb()), "trails");
    }

    @Test
    void everyPresetGoesAroundTheFullSphere() {
        // Below the horizon the void of a sky island shows, and a sky that turns brings that part of the design up.
        for (SkyPresets.Preset p : SkyPresets.all()) {
            assertEquals(SkyDesign.Extent.SPHERE, p.design().withTurning(SkyTurning.STILL).drawnExtent(), p.key());
        }
    }

    @Test
    void noSeamWhereTheImageWrapsAndNoPinchAtTheZenith() {
        int[] px = SkyGenerator.bake(of(layer(1, SkyLayer.Kind.NOISE).withTransform(new Transform(0, 0, 0, 400))), W, H).argb();
        for (int y = 0; y < H; y++) {
            assertTrue(Math.abs(red(px[y * W]) - red(px[y * W + W - 1])) < 24, "row " + y);
        }
        int min = 255, max = 0;
        for (int x = 0; x < W; x++) {
            min = Math.min(min, red(px[x]));
            max = Math.max(max, red(px[x]));
        }
        assertTrue(max - min < 24, "the top row is one point of the sky");
    }

    @Test
    void theGradientRunsFromTheLowColorAtTheHorizonToTheHighColorAtTheZenith() {
        SkyImage img = SkyGenerator.bake(of(layer(1, SkyLayer.Kind.GRADIENT)), W, H);
        int top = img.argb()[W / 2], bottom = img.argb()[(H - 1) * W + W / 2];
        assertTrue(Math.abs(red(top) - 0x0B) <= 3, Integer.toHexString(top));
        assertTrue(red(bottom) > 0xB0, Integer.toHexString(bottom));
        float[] fog = img.fogRgb(SkyTurn.STILL, 0, 1);
        assertTrue(fog[0] > 0.7f && fog[2] < 0.5f, "the fog takes the horizon color: " + Arrays.toString(fog));
        assertEquals(0f, img.fogRgb(SkyTurn.STILL, 0, 0)[0], 0.02f, "a dark night darkens the fog");
    }

    @Test
    void theSkyFromThreeDegreesBelowTheHorizonHasTheFogColorOfItsDirection() {
        SkyLayer noise = layer(1, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.AMOUNT, 100).withTransform(new Transform(0, 0, 0, 40));
        int w = 256, h = 64;
        SkyImage img = SkyGenerator.bake(of(noise), w, h);
        for (int y = row(-3, h) + 1; y < h; y++) {
            for (int x = 0; x < w; x += 7) {
                float[] fog = img.fogRgb(SkyTurn.STILL, (float) ((x + 0.5) / w * 360 - 180), 1);
                assertEquals(Math.round(fog[0] * 255), red(img.argb()[y * w + x]), 2, "row " + y + ", column " + x);
            }
        }
    }

    @Test
    void galaxyDustTakesAwayLightAndJetsAddLightAlongTheAxis() {
        int w = 512, h = 256;
        // A tilted disc in the north, 30 degrees up, with its axis vertical.
        SkyLayer galaxy = layer(1, SkyLayer.Kind.GALAXY).withTransform(new Transform(0, 30, 0, 200)).withParam(SkyLayer.Param.TILT, 60);
        SkyLayer clean = galaxy.withParam(SkyLayer.Param.DUST, 0).withParam(SkyLayer.Param.JETS, 0);
        int[] plain = SkyGenerator.bake(of(clean), w, h).argb();
        int[] dusty = SkyGenerator.bake(of(clean.withParam(SkyLayer.Param.DUST, 100)), w, h).argb();
        int[] jets = SkyGenerator.bake(of(clean.withParam(SkyLayer.Param.JETS, 100)), w, h).argb();
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
        SkyLayer galaxy = layer(1, SkyLayer.Kind.GALAXY).withTransform(new Transform(0, 30, 0, 200));
        assertEquals(0, galaxy.param(SkyLayer.Param.DETAIL) + galaxy.param(SkyLayer.Param.MOTION), "a new galaxy is plain and still");
        assertEquals(-1082905965, Arrays.hashCode(SkyGenerator.bake(design(galaxy), 512, 256).argb()));
    }

    @Test
    void galaxyDetailAddsItsPartsAndItsDustStillOnlyAbsorbs() {
        int w = 512, h = 256;
        SkyLayer plain = layer(1, SkyLayer.Kind.GALAXY).withTransform(new Transform(0, 30, 0, 200)).withParam(SkyLayer.Param.TILT, 60)
                .withParam(SkyLayer.Param.JETS, 0);
        SkyLayer fine = plain.withParam(SkyLayer.Param.DETAIL, 100);
        int[] before = SkyGenerator.bake(of(plain), w, h).argb(), after = SkyGenerator.bake(of(fine), w, h).argb();
        int changed = 0;
        for (int i = 0; i < before.length; i++) if (Math.abs(red(before[i]) - red(after[i])) > 2) changed++;
        assertTrue(changed > 1500, "the detail changes the disc and the halo: " + changed + " pixels");
        int[] clean = SkyGenerator.bake(of(fine.withParam(SkyLayer.Param.DUST, 0)), w, h).argb();
        int[] dusty = SkyGenerator.bake(of(fine.withParam(SkyLayer.Param.DUST, 100)), w, h).argb();
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
        SkyGenerator.galaxy(l, new float[] {px / len, py / len, -1 / len}, texel, time, out);
        return new float[] {out[0] * out[3], out[1] * out[3], out[2] * out[3]};
    }

    private static float[] galaxyLight(SkyLayer galaxy, float x, float y, float time) {
        return galaxyLight(galaxy, x, y, time, 0.0005f);
    }

    @Test
    void aGalaxyMovesOnlyWithMotionAndComesBackToItsStartInTheHourOfTheSkyClock() {
        SkyLayer still = layer(1, SkyLayer.Kind.GALAXY).withTransform(Transform.IDENTITY).withParam(SkyLayer.Param.DETAIL, 70);
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
        SkyLayer moving = layer(1, SkyLayer.Kind.GALAXY).withTransform(Transform.IDENTITY).withParam(SkyLayer.Param.JETS, 0)
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
            "float ALBEDO_GALAXY_REACH = " + SkyGenerator.GALAXY_REACH,
            "float ALBEDO_GALAXY_JET_LENGTH = " + SkyGenerator.JET_LENGTH,
            "float ALBEDO_GALAXY_KNOT_CELL = " + SkyGenerator.KNOT_CELL,
            "vec3 ALBEDO_GALAXY_DUST_REDDENING = " + vec3(SkyGenerator.DUST_REDDENING),
            "vec3 ALBEDO_GALAXY_HALPHA = " + vec3(SkyGenerator.HALPHA),
            "vec3 ALBEDO_GALAXY_LOBE = " + vec3(SkyGenerator.LOBE),
            "vec3 ALBEDO_GALAXY_NUCLEUS = " + vec3(SkyGenerator.NUCLEUS),
            "float ALBEDO_GALAXY_DUST_THREADS = " + SkyGenerator.DUST_THREADS,
            "float ALBEDO_GALAXY_GAS_CELL = " + SkyGenerator.GAS_CELL,
            "float ALBEDO_GALAXY_CLUSTER_CELL = " + SkyGenerator.CLUSTER_CELL,
            "float ALBEDO_GALAXY_LOOP = " + SkyGenerator.LOOP,
            "vec3 ALBEDO_GALAXY_JET_END = " + vec3(SkyGenerator.JET_END),
            "vec3 ALBEDO_GALAXY_LOBE_RIM = " + vec3(SkyGenerator.LOBE_RIM),
            "vec3 ALBEDO_GALAXY_HOT_SPOT = " + vec3(SkyGenerator.HOT_SPOT),
            "vec3 ALBEDO_GALAXY_BOW_SHOCK = " + vec3(SkyGenerator.BOW_SHOCK),
            "vec3 ALBEDO_GALAXY_ION_CONE = " + vec3(SkyGenerator.ION_CONE),
            "vec3 ALBEDO_GALAXY_GAS_CORE = " + vec3(SkyGenerator.GAS_CORE),
            "vec3 ALBEDO_GALAXY_BULGE_CORE = " + vec3(SkyGenerator.BULGE_CORE),
            "vec3 ALBEDO_GALAXY_BULGE_EDGE = " + vec3(SkyGenerator.BULGE_EDGE),
            "vec3 ALBEDO_GALAXY_DUST_GLOW = " + vec3(SkyGenerator.DUST_GLOW),
            "vec3 ALBEDO_GALAXY_STREAM = " + vec3(SkyGenerator.STREAM),
            "vec3 ALBEDO_GALAXY_SUPERNOVA = " + vec3(SkyGenerator.SUPERNOVA),
            "float ALBEDO_GALAXY_STREAM_TURN = " + SkyGenerator.STREAM_TURN,
            "float ALBEDO_GALAXY_STREAM_HEAD = " + SkyGenerator.STREAM_HEAD,
            "float ALBEDO_GALAXY_STREAM_LONG = " + SkyGenerator.STREAM_LONG};
        for (String n : numbers) assertTrue(glsl.contains("const " + n + ";\n"), n);
    }

    @Test
    void aStarHasTheSameSizeAtEachCount() {
        int w = 1024, h = 512;
        int few = lit(SkyGenerator.bake(of(layer(1, SkyLayer.Kind.STARS).withParam(SkyLayer.Param.AMOUNT, 20)), w, h));
        int many = lit(SkyGenerator.bake(of(layer(1, SkyLayer.Kind.STARS).withParam(SkyLayer.Param.AMOUNT, 80)), w, h));
        // The chance that a cell has a star is (0.2 + 0.8 * count) squared, about 5.4 times more at 80 than at 20, and the star size stays.
        float ratio = many / (float) few;
        assertTrue(ratio > 3.5f && ratio < 8f, "lit pixels " + few + " and " + many);
    }

    /** The count of light pixels above the horizon, where the stars are. */
    private static int lit(SkyImage img) {
        int n = 0;
        for (int i = 0; i < img.argb().length / 2; i++) if (red(img.argb()[i]) > 60) n++;
        return n;
    }

    @Test
    void duskShowsNoStarsLowInTheSky() {
        SkyDesign noStars = SkyPresets.DUSK.withoutLayer(SkyPresets.DUSK.layers().size() - 1);
        int[] with = SkyGenerator.bake(SkyPresets.DUSK, W * 4, H * 4).argb(), without = SkyGenerator.bake(noStars, W * 4, H * 4).argb();
        int low = (int) ((SkyImage.TOP - 25) / (SkyImage.TOP - SkyImage.BOTTOM) * H * 4), more = 0;
        for (int i = low * W * 4; i < with.length; i++) assertTrue(Math.abs(red(with[i]) - red(without[i])) <= 3, "pixel " + i);
        for (int i = 0; i < low * W * 4; i++) if (red(with[i]) - red(without[i]) > 20) more++;
        assertTrue(more > 0, "stars show high in the sky");
    }

    @Test
    void aBlurOfOneColorChangesNothing() {
        SkyLayer flat = layer(1, SkyLayer.Kind.GRADIENT).withParam(SkyLayer.Param.COLOR_2, 0xFFF08A4B);
        SkyLayer blur = layer(2, SkyLayer.Kind.BLUR).withParam(SkyLayer.Param.AMOUNT, 100);
        for (SkyDesign.Extent e : SkyDesign.Extent.values()) {
            int[] plain = SkyGenerator.bake(of(flat).withExtent(e), W, H).argb();
            int[] blurred = SkyGenerator.bake(of(flat, blur).withExtent(e), W, H).argb();
            for (int i = 0; i < plain.length; i++) assertTrue(Math.abs(red(plain[i]) - red(blurred[i])) <= 1, e + " pixel " + i);
        }
    }

    @Test
    void filtersAtTheHorizonDoNotReadTheRowsThatTheLayersLeaveEmpty() {
        SkyLayer flat = layer(1, SkyLayer.Kind.GRADIENT).withParam(SkyLayer.Param.COLOR_2, 0xFFF08A4B);
        SkyDesign d = of(flat, layer(2, SkyLayer.Kind.BLUR).withParam(SkyLayer.Param.AMOUNT, 100), layer(3, SkyLayer.Kind.BLOOM));
        int w = 64, h = 512;
        int[] px = SkyGenerator.bake(d, w, h).argb();
        int high = row(45, h);
        for (int y = row(15, h); y < row(3, h); y++) {
            for (int x = 0; x < w; x++) assertEquals(red(px[high * w + x]), red(px[y * w + x]), 1, "row " + y + ", column " + x);
        }
    }

    @Test
    void aFullSphereHasTheLayersAtAndBelowTheHorizon() {
        SkyLayer noise = layer(1, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.AMOUNT, 100).withTransform(new Transform(0, 0, 0, 40));
        int w = 256, h = 1024;
        SkyImage horizon = SkyGenerator.bake(of(noise), w, h);
        SkyImage sphere = SkyGenerator.bake(of(noise).withExtent(SkyDesign.Extent.SPHERE), w, h);
        int low = row(-45, h);
        for (int x = 0; x < w; x++) {
            float[] fog = horizon.fogRgb(SkyTurn.STILL, (float) ((x + 0.5) / w * 360 - 180), 1);
            assertEquals(Math.round(fog[0] * 255), red(horizon.argb()[low * w + x]), 2, "the horizon sky is the fog color at -45");
        }
        assertTrue(spread(sphere, low) > 40, "the layers continue below the horizon");
        assertTrue(spread(sphere, h / 2) > 40, "the layers continue just below the horizon");
        int above = spread(horizon, h / 2 - 1), fading = spread(horizon, row(-1.5, h));
        assertTrue(above > 40, "the layers go down to the horizon when the sky stops there");
        assertTrue(fading > 2 && fading < above, "the layers go into the horizon color below it: " + fading);
        for (int i = 0; i < h / 2 * w; i++) assertEquals(horizon.argb()[i], sphere.argb()[i], "the sky above the horizon is the same");
    }

    /** The difference between the darkest and the lightest red value in a row. */
    private static int spread(SkyImage img, int y) {
        int min = 255, max = 0;
        for (int x = 0; x < img.width(); x++) {
            min = Math.min(min, red(img.argb()[y * img.width() + x]));
            max = Math.max(max, red(img.argb()[y * img.width() + x]));
        }
        return max - min;
    }

    @Test
    void aMaskShowsALayerOnlyWhereTheMaskLayerIsLight() {
        SkyLayer black = layer(1, SkyLayer.Kind.GRADIENT).withParam(SkyLayer.Param.COLOR_1, 0xFF000000)
                .withParam(SkyLayer.Param.COLOR_2, 0xFF000000);
        SkyLayer mask = layer(2, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.COLOR_1, 0xFF000000)
                .withParam(SkyLayer.Param.COLOR_2, 0xFFFFFFFF).withParam(SkyLayer.Param.AMOUNT, 100).withOutput(SkyLayer.Output.MASK_ONLY);
        SkyLayer white = layer(3, SkyLayer.Kind.GRADIENT).withParam(SkyLayer.Param.COLOR_1, 0xFFFFFFFF)
                .withParam(SkyLayer.Param.COLOR_2, 0xFFFFFFFF);
        int[] none = SkyGenerator.bake(of(black, mask), W, H).argb();
        for (int p : none) assertEquals(0, red(p), "a mask layer does not show");
        int[] light = SkyGenerator.bake(of(black, mask, white.withMask(SkyMask.of(2, SkyMask.Mode.LIGHT))), W, H).argb();
        int[] dark = SkyGenerator.bake(of(black, mask, white.withMask(SkyMask.of(2, SkyMask.Mode.DARK))), W, H).argb();
        int[] all = SkyGenerator.bake(of(black, mask, white), W, H).argb();
        int varied = 0;
        for (int i = 0; i < light.length; i++) {
            assertEquals(255, red(all[i]));
            float sum = Oklab.toLinear(red(light[i])) + Oklab.toLinear(red(dark[i]));
            assertEquals(1f, sum, 0.03f, "light and dark split the layer");
            if (red(light[i]) > 160 || red(light[i]) < 90) varied++;
        }
        assertTrue(varied > light.length / 10, "the mask changes across the sky");
    }

    @Test
    void everyKindBakesAndAnEmptySkyIsBlack() {
        for (SkyLayer.Kind k : SkyLayer.Kind.values()) {
            SkyImage img = SkyGenerator.bake(of(layer(1, SkyLayer.Kind.GRADIENT), layer(2, k)), 64, 16);
            assertEquals(64 * 16, img.argb().length, k.key());
        }
        for (int p : SkyGenerator.bake(new SkyDesign(Collections.<SkyLayer>emptyList(), SkyDesign.Celestial.SHOW,
                SkyDesign.Light.FIXED, SkyDesign.Extent.SPHERE), 16, 4).argb()) {
            assertEquals(0xFF000000, p);
        }
    }

    @Test
    void aRotatedLayerEqualsTheLayerSampledAtTheRotatedDirection() {
        SkyLayer noise = layer(1, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.AMOUNT, 100);
        int[] plain = SkyGenerator.bake(of(noise), 360, 32).argb();
        int[] turned = SkyGenerator.bake(of(noise.withTransform(new Transform(90, 0, 0, 100))), 360, 32).argb();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 360; x++) assertEquals(red(plain[y * 360 + x]), red(turned[y * 360 + (x + 90) % 360]), 2);
        }
    }

    @Test
    void aFullSkyBakesFastEnough() {
        long start = System.nanoTime();
        SkyGenerator.bake(SkyPresets.NEBULA, 512, 128);
        assertTrue((System.nanoTime() - start) / 1000000 < 3000, "512x128 on one thread");
    }

    @Test
    void theBloomTermHoldsOnlyTheGlowThatTheBloomAdds() {
        int w = 128, h = 64;
        SkyImage full = SkyGenerator.bake(SkyPresets.GALAXY, w, h);
        int bloom = SkyPresets.GALAXY.layers().size() - 1;
        SkyImage below = SkyGenerator.bake(SkyPresets.GALAXY.withLayers(SkyPresets.GALAXY.layers().subList(0, bloom)), w, h);
        assertEquals(w / 2 * h / 2 * 4 * 2, below.terms().length, "the field images of the noise and the fractal");
        assertEquals(w / 2, full.termTextureWidth());
        assertEquals(h / 2 * 3, full.termTextureHeight(), "the bloom term over the field images of the noise and the fractal");
        assertEquals(w / 2, full.termWidth());
        assertEquals(h / 2, full.termHeight());
        assertEquals(w / 2 * h / 2 * 4 * 3, full.terms().length, "three images of half float RGBA");
        double error = 0, glow = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int c = 0; c < 3; c++) {
                    int shift = 16 - 8 * c;
                    float added = Oklab.toLinear(full.argb()[y * w + x] >> shift) - Oklab.toLinear(below.argb()[y * w + x] >> shift);
                    float term = HalfFloat.decode(full.terms()[((y / 2) * (w / 2) + x / 2) * 4 + c]);
                    error += Math.abs(added - term);
                    glow = Math.max(glow, term);
                    count++;
                }
            }
        }
        assertTrue(glow > 0.005, "the galaxy glows: " + glow);
        assertTrue(error / count < 0.004, "the term is the added glow: " + error / count);
    }

    @Test
    void theFieldImagesHoldTheSmoothNoiseAtTheTexelCenters() {
        int w = 64, h = 32;
        SkyLayer clouds = layer(1, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.PULSE, 50);
        SkyLayer fractal = layer(2, SkyLayer.Kind.FRACTAL);
        SkyImage img = SkyGenerator.bake(of(clouds, fractal), w, h);
        assertEquals(0, img.program().termSlot(0));
        assertEquals(1, img.program().termSlot(1));
        int tw = img.termWidth(), th = img.termHeight();
        boolean negative = false;
        for (int ty = 0; ty < th; ty += 5) {
            for (int tx = 0; tx < tw; tx += 7) {
                // The texel center in the mapping of albedoField, where the layers have no turn.
                double el = Math.toRadians(90 - (ty + 0.5) * 180 / th), lon = (tx + 0.5) / tw * 2 * Math.PI;
                float[] dir = direction(lon, el);
                float x = dir[0], y = dir[1], z = dir[2];
                int noise = (ty * tw + tx) * 4, bend = ((th + ty) * tw + tx) * 4;
                float coarse = Noise.fbmPart(x * 2.5f, y * 2.5f, z * 2.5f, 1, 0, SkyGenerator.CLOUD_FIELD_OCTAVES, SkyGenerator.CLOUD_OCTAVES);
                assertEquals(coarse * 0.5f + 0.5f, HalfFloat.decode(img.terms()[noise]), 1e-3f, "the coarse octaves of the clouds");
                float fine = Noise.fbmPart(x * 2.5f, y * 2.5f, z * 2.5f, 1, SkyGenerator.CLOUD_FIELD_OCTAVES, SkyGenerator.CLOUD_OCTAVES,
                        SkyGenerator.CLOUD_OCTAVES);
                assertEquals(Noise.fbm(x * 2.5f, y * 2.5f, z * 2.5f, 1, SkyGenerator.CLOUD_OCTAVES), coarse + fine, 1e-5f, "the parts add up");
                assertEquals(Noise.noise(x * 2.2f, y * 2.2f, z * 2.2f, 1 + 71), HalfFloat.decode(img.terms()[noise + 1]), 1e-3f, "the pulse noise");
                float bendX = Noise.fbm(x * 1.6f + 5.2f, y * 1.6f + 1.3f, z * 1.6f + 2.8f, 1 + 11, 4);
                assertEquals(bendX, HalfFloat.decode(img.terms()[bend]), 2e-3f, "the bend of the fractal");
                assertEquals(0f, HalfFloat.decode(img.terms()[bend + 3]), "a fractal with no pulse");
                negative |= bendX < -0.01f;
            }
        }
        assertTrue(negative, "a bend can be negative");
    }
}
