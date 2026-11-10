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

import static org.junit.jupiter.api.Assertions.*;

class SkyGeneratorTest {
    private static final int W = 128, H = 32;

    private static SkyDesign of(SkyLayer... layers) {
        return new SkyDesign(Arrays.asList(layers), SkyDesign.Celestial.SHOW, SkyDesign.Light.FIXED, SkyDesign.Extent.HORIZON);
    }

    private static SkyLayer layer(int id, SkyLayer.Kind kind) {
        return SkyLayer.of(id, kind);
    }

    private static int red(int argb) {
        return argb >> 16 & 0xFF;
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
            double az = i * 0.7, el = (i % 17) * 0.09 - 0.7;
            float[] p = {(float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (-Math.cos(el) * Math.cos(az))};
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
        assertEquals(1164738102, Arrays.hashCode(SkyGenerator.bake(SkyPresets.DUSK, 256, 128).argb()), "dusk");
        assertEquals(-1992624876, Arrays.hashCode(SkyGenerator.bake(SkyPresets.NEBULA, 256, 128).argb()), "nebula");
        assertEquals(2083471237, Arrays.hashCode(SkyGenerator.bake(SkyPresets.GALAXY, 256, 128).argb()), "galaxy");
        assertEquals(-1617945180, Arrays.hashCode(SkyGenerator.bake(SkyPresets.METEORS, 256, 128).argb()), "meteors");
        assertEquals(-1710622170, Arrays.hashCode(SkyGenerator.bake(SkyPresets.AURORA, 256, 128).argb()), "aurora");
        assertEquals(-1457549582, Arrays.hashCode(SkyGenerator.bake(SkyPresets.TRAILS, 256, 128).argb()), "trails");
    }

    @Test
    void theBakerInParallelGivesTheSamePixels() throws InterruptedException {
        SkyBaker baker = new SkyBaker(W, H, e -> fail(e));
        baker.request(SkyPresets.NEBULA);
        for (int i = 0; i < 400 && !baker.current(); i++) Thread.sleep(10);
        assertTrue(baker.current());
        assertArrayEquals(SkyGenerator.bake(SkyPresets.NEBULA, W, H).argb(), baker.image().argb());
    }

    @Test
    void aNewRenderKeepsThePixelsWithNoBake() throws InterruptedException {
        SkyBaker baker = new SkyBaker(W, H, e -> fail(e));
        baker.request(SkyPresets.NEBULA);
        for (int i = 0; i < 400 && !baker.current(); i++) Thread.sleep(10);
        SkyImage sharp = baker.image();
        assertTrue(sharp.drawsSharp());
        baker.request(SkyPresets.NEBULA.withRender(SkyDesign.Render.FAST));
        assertTrue(baker.current(), "the image is ready at once");
        assertSame(sharp.argb(), baker.image().argb());
        assertNotEquals(sharp.serial(), baker.image().serial(), "the adapters see a new image");
        assertFalse(baker.image().drawsSharp());
        SkyDesign turning = SkyPresets.NEBULA.withTurning(SkyTurning.STILL.withAmount(50));
        baker.request(turning);
        assertTrue(baker.current(), "a new turning also keeps the pixels");
        assertSame(sharp.argb(), baker.image().argb());
        assertEquals(turning, baker.image().design());
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
        float[] fog = img.fogRgb(0, 1);
        assertTrue(fog[0] > 0.7f && fog[2] < 0.5f, "the fog takes the horizon color: " + Arrays.toString(fog));
        assertEquals(fog[0] * 0, img.fogRgb(0, 0)[0], 0.02f, "a dark night darkens the fog");
    }

    @Test
    void theSkyFromThreeDegreesBelowTheHorizonHasTheFogColorOfItsDirection() {
        SkyLayer noise = layer(1, SkyLayer.Kind.NOISE).withParam(SkyLayer.Param.AMOUNT, 100).withTransform(new Transform(0, 0, 0, 40));
        int w = 256, h = 64;
        SkyImage img = SkyGenerator.bake(of(noise), w, h);
        for (int y = row(-3, h) + 1; y < h; y++) {
            for (int x = 0; x < w; x += 7) {
                float[] fog = img.fogRgb((float) ((x + 0.5) / w * 360 - 180), 1);
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
    void theShaderHasTheSameGalaxyNumbers() throws IOException {
        String glsl = SkyProgramTest.skyLayersSource();
        String[] numbers = {
            "float ALBEDO_GALAXY_REACH = " + SkyGenerator.GALAXY_REACH,
            "float ALBEDO_GALAXY_JET_LENGTH = " + SkyGenerator.JET_LENGTH,
            "float ALBEDO_GALAXY_KNOT_CELL = " + SkyGenerator.KNOT_CELL,
            "vec3 ALBEDO_GALAXY_DUST_REDDENING = " + vec3(SkyGenerator.DUST_REDDENING),
            "vec3 ALBEDO_GALAXY_HALPHA = " + vec3(SkyGenerator.HALPHA),
            "vec3 ALBEDO_GALAXY_LOBE = " + vec3(SkyGenerator.LOBE),
            "vec3 ALBEDO_GALAXY_NUCLEUS = " + vec3(SkyGenerator.NUCLEUS)};
        for (String n : numbers) assertTrue(glsl.contains("const " + n + ";\n"), n);
    }

    private static String vec3(float[] v) {
        return "vec3(" + v[0] + ", " + v[1] + ", " + v[2] + ")";
    }

    private static int blue(int argb) {
        return argb & 0xFF;
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
            float[] fog = horizon.fogRgb((float) ((x + 0.5) / w * 360 - 180), 1);
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

    /** The row whose center is nearest to an elevation in degrees. */
    private static int row(double elevation, int h) {
        return (int) Math.round((SkyImage.TOP - elevation) / (SkyImage.TOP - SkyImage.BOTTOM) * h - 0.5);
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
    void halfFloatsRoundToTheNearestAndStayInRange() {
        assertEquals(0x3C00, SkyGenerator.half(1f));
        assertEquals(0x3800, SkyGenerator.half(0.5f));
        assertEquals(0x3C01, SkyGenerator.half(1.0009766f));
        assertEquals(0x3555, SkyGenerator.half(1f / 3));
        assertEquals(0x7BFF, SkyGenerator.half(65504f));
        assertEquals(0x7BFF, SkyGenerator.half(1e9f), "the largest half float");
        assertEquals(0, SkyGenerator.half(-1f), "no negative glow");
        assertEquals(0, SkyGenerator.half(Float.NaN), "no bright speck from a NaN");
        assertEquals(1, SkyGenerator.half(5.9604645e-8f), "the smallest subnormal");
        assertEquals(0x0200, SkyGenerator.half(3.0517578e-5f), "a subnormal");
        for (float v = 1e-6f; v < 60000; v *= 1.37f) assertEquals(v, fromHalf(SkyGenerator.half(v)), v / 1024 + 6e-8f, "" + v);
    }

    @Test
    void theBloomTermHoldsOnlyTheGlowThatTheBloomAdds() {
        int w = 128, h = 64;
        SkyImage full = SkyGenerator.bake(SkyPresets.GALAXY, w, h);
        SkyImage below = SkyGenerator.bake(SkyPresets.GALAXY.withLayers(SkyPresets.GALAXY.layers().subList(0, 4)), w, h);
        assertEquals(0, below.terms().length);
        assertEquals(1, below.termTextureWidth() * below.termTextureHeight(), "one texel for the sampler");
        assertEquals(w / 2, full.termTextureWidth());
        assertEquals(h / 2, full.termTextureHeight());
        assertEquals(w / 2, full.termWidth());
        assertEquals(h / 2, full.termHeight());
        assertEquals(w / 2 * h / 2 * 4, full.terms().length, "one term image of half float RGBA");
        double error = 0, glow = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int c = 0; c < 3; c++) {
                    int shift = 16 - 8 * c;
                    float added = Oklab.toLinear(full.argb()[y * w + x] >> shift) - Oklab.toLinear(below.argb()[y * w + x] >> shift);
                    float term = fromHalf(full.terms()[((y / 2) * (w / 2) + x / 2) * 4 + c]);
                    error += Math.abs(added - term);
                    glow = Math.max(glow, term);
                    count++;
                }
            }
        }
        assertTrue(glow > 0.005, "the galaxy glows: " + glow);
        assertTrue(error / count < 0.004, "the term is the added glow: " + error / count);
    }

    static float fromHalf(short bits) {
        int exp = (bits >> 10) & 0x1F, mantissa = bits & 0x3FF;
        return exp == 0 ? mantissa / 16777216f : (float) ((1 + mantissa / 1024.0) * Math.pow(2, exp - 15));
    }

    @Test
    void daylightFollowsTheVanillaSky() {
        assertEquals(1f, SkyLight.brightness(6000, 0, 0), 1e-4f);
        assertEquals(0.04f, SkyLight.brightness(18000, 0, 0), 1e-4f);
        assertEquals(0.7f, SkyLight.brightness(6000, 1, 0), 1e-4f);
        assertEquals(1f, SkyLight.of(SkyPresets.NEBULA, 18000, 0, 0));
    }
}
