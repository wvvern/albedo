package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SkyGeneratorTest {
    private static final int W = 128, H = 32;

    private static SkyDesign of(SkyLayer... layers) {
        return new SkyDesign(Arrays.asList(layers), SkyDesign.Celestial.SHOW, SkyDesign.Light.FIXED);
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
    void theBakerInParallelGivesTheSamePixels() throws InterruptedException {
        SkyBaker baker = new SkyBaker(W, H, e -> fail(e));
        baker.request(SkyPresets.NEBULA);
        for (int i = 0; i < 400 && !baker.current(); i++) Thread.sleep(10);
        assertTrue(baker.current());
        assertArrayEquals(SkyGenerator.bake(SkyPresets.NEBULA, W, H).argb(), baker.image().argb());
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
        assertTrue(red(bottom) > 0xD0, Integer.toHexString(bottom));
        float[] fog = img.fogRgb(0, 1);
        assertTrue(fog[0] > 0.7f && fog[2] < 0.5f, "the fog takes the horizon color: " + Arrays.toString(fog));
        assertEquals(fog[0] * 0, img.fogRgb(0, 0)[0], 0.02f, "a dark night darkens the fog");
    }

    @Test
    void aBlurOfOneColorChangesNothing() {
        SkyLayer flat = layer(1, SkyLayer.Kind.GRADIENT).withParam(SkyLayer.Param.COLOR_2, 0xFFF08A4B);
        int[] plain = SkyGenerator.bake(of(flat), W, H).argb();
        int[] blurred = SkyGenerator.bake(of(flat, layer(2, SkyLayer.Kind.BLUR).withParam(SkyLayer.Param.AMOUNT, 100)), W, H).argb();
        for (int i = 0; i < plain.length; i++) assertTrue(Math.abs(red(plain[i]) - red(blurred[i])) <= 1);
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
                SkyDesign.Light.FIXED), 16, 4).argb()) {
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
    void daylightFollowsTheVanillaSky() {
        assertEquals(1f, SkyLight.brightness(6000, 0, 0), 1e-4f);
        assertEquals(0.04f, SkyLight.brightness(18000, 0, 0), 1e-4f);
        assertEquals(0.7f, SkyLight.brightness(6000, 1, 0), 1e-4f);
        assertEquals(1f, SkyLight.of(SkyPresets.NEBULA, 18000, 0, 0));
    }
}
