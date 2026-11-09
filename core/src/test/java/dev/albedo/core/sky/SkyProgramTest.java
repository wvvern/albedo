package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Transform;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkyProgramTest {
    @Test
    void theBlockIsAWholeNumberOfVec4Values() {
        assertEquals(0, SkyProgram.FLOATS % 4);
        assertEquals(4 + SkyDesign.MAX_LAYERS * SkyProgram.LAYER_VEC4S * 4 + SkyImage.RING * 4, SkyProgram.FLOATS);
        assertEquals(SkyProgram.LAYER_VEC4S * 4, SkyProgram.LOCAL + 12, "the rotation rows end the layer");
    }

    @Test
    void theShaderHasTheSameLayoutAndCodes() throws IOException {
        String glsl = skyLayersSource();
        String[] defines = {"MAX_LAYERS " + SkyDesign.MAX_LAYERS, "LAYER_VEC4S " + SkyProgram.LAYER_VEC4S, "RING " + SkyImage.RING,
            "KIND_GRADIENT " + SkyProgram.KIND_GRADIENT, "KIND_GLOW " + SkyProgram.KIND_GLOW, "KIND_STARS " + SkyProgram.KIND_STARS,
            "KIND_NOISE " + SkyProgram.KIND_NOISE, "KIND_FRACTAL " + SkyProgram.KIND_FRACTAL, "KIND_GALAXY " + SkyProgram.KIND_GALAXY,
            "KIND_BLOOM " + SkyProgram.KIND_BLOOM, "KIND_BLUR " + SkyProgram.KIND_BLUR, "KIND_PIXELATE " + SkyProgram.KIND_PIXELATE,
            "KIND_CRYSTALLIZE " + SkyProgram.KIND_CRYSTALLIZE, "KIND_SWIRL " + SkyProgram.KIND_SWIRL,
            "KIND_POSTERIZE " + SkyProgram.KIND_POSTERIZE, "KIND_HUE_SHIFT " + SkyProgram.KIND_HUE_SHIFT,
            "KIND_VIGNETTE " + SkyProgram.KIND_VIGNETTE, "KIND_METEORS " + SkyProgram.KIND_METEORS, "KIND_AURORA " + SkyProgram.KIND_AURORA,
            "METEOR_CHANNELS " + SkyMeteors.CHANNELS, "METEOR_SLOTS_BACK " + SkyMeteors.SLOTS_BACK,
            "BLEND_NORMAL " + SkyProgram.BLEND_NORMAL, "BLEND_ADD " + SkyProgram.BLEND_ADD, "BLEND_SCREEN " + SkyProgram.BLEND_SCREEN,
            "BLEND_MULTIPLY " + SkyProgram.BLEND_MULTIPLY, "USE_NONE " + SkyProgram.USE_NONE, "USE_MASK " + SkyProgram.USE_MASK,
            "USE_SHOW " + SkyProgram.USE_SHOW};
        for (String d : defines) assertTrue(glsl.contains("#define ALBEDO_" + d + "\n"), d);
        // The shader reads the values of a layer from these vec4 slots.
        assertEquals(4, SkyProgram.MASK_DARK);
        assertEquals(8, SkyProgram.SCALE);
        assertEquals(12, SkyProgram.COLOR_1);
        assertEquals(15, SkyProgram.TERM_SLOT);
        assertTrue(glsl.contains("int(albedoLayer(layer, 3).w)"), "the term slot is the last value of slot 3");
        assertEquals(20, SkyProgram.LAB_1);
        assertEquals(24, SkyProgram.LAB_2);
        assertEquals(28, SkyProgram.LOCAL);
        assertTrue(glsl.contains("albedoLayer(i, 7)"), "the rotation starts at slot 7");
        // The meteors read their speed, length and shower from the last value of slots 4, 5 and 6, and the aurora reads its speed there too.
        assertEquals(4 * 4 + 3, SkyProgram.SPEED);
        assertTrue(glsl.contains("float t = max(SkyHeader.w, 0.0) * albedoLayer(layer, 4).w * 2.0;"), "the aurora reads the header time");
        assertFalse(glsl.contains("albedoTime"), "the sky has one time, in the header");
        assertEquals(5 * 4 + 3, SkyProgram.LENGTH);
        assertEquals(6 * 4 + 3, SkyProgram.SHOWER);
        assertTrue(glsl.contains("const float ALBEDO_METEOR_SLOT = " + SkyMeteors.SLOT + ";"));
        assertTrue(glsl.contains("const float ALBEDO_METEOR_TRAIL_END = " + SkyMeteors.TRAIL_END + ";"));
    }

    @Test
    void theMeteorsPresetDrawsSharpAndMovesWithTheTime() {
        SkyProgram meteors = SkyGenerator.bake(SkyPresets.METEORS, 64, 32).program();
        assertTrue(meteors.drawsSharp());
        assertTrue(meteors.moves());
        assertEquals(SkyProgram.STILL, meteors.block()[SkyProgram.TIME], "the block is a still sky until the adapter writes the time");
        assertEquals(SkyProgram.KIND_METEORS, meteors.block()[SkyProgram.at(3, SkyProgram.KIND)]);
        assertEquals(0.7f, meteors.block()[SkyProgram.at(3, SkyProgram.SHOWER)], 1e-6f);
        assertFalse(SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program().moves());
        SkyDesign hidden = SkyPresets.METEORS.withLayer(3, SkyPresets.METEORS.layers().get(3).withOutput(SkyLayer.Output.MASK_ONLY))
                .withLayer(2, SkyPresets.METEORS.layers().get(2).withParam(SkyLayer.Param.TWINKLE, 0));
        assertFalse(SkyProgram.of(hidden, new float[SkyImage.RING * 3]).moves(), "a meteors layer that does not show or mask does not move");
        SkyLayer masked = SkyLayer.of(5, SkyLayer.Kind.GRADIENT).withMask(SkyMask.of(4, SkyMask.Mode.LIGHT));
        List<SkyLayer> layers = new ArrayList<SkyLayer>(hidden.layers());
        layers.add(masked);
        assertTrue(SkyProgram.of(hidden.withLayers(layers), new float[SkyImage.RING * 3]).moves(), "a meteors mask moves");
        SkyProgram aurora = SkyGenerator.bake(SkyPresets.AURORA, 64, 32).program();
        assertTrue(aurora.moves(), "the aurora moves with the same time");
        assertEquals(SkyProgram.STILL, aurora.block()[SkyProgram.TIME]);
    }

    @Test
    void theTwinkleAndThePulseMoveTheSkyOnlyWhenTheyAreOn() throws IOException {
        SkyLayer stars = SkyLayer.of(1, SkyLayer.Kind.STARS), clouds = SkyLayer.of(2, SkyLayer.Kind.FRACTAL);
        assertFalse(program(stars, clouds).moves(), "the default stars and clouds are still");
        SkyProgram twinkling = program(stars.withParam(SkyLayer.Param.TWINKLE, 40), clouds);
        assertTrue(twinkling.moves());
        assertEquals(0.4f, twinkling.block()[SkyProgram.at(0, SkyProgram.TWINKLE)], 1e-6f);
        SkyProgram pulsing = program(stars, clouds.withParam(SkyLayer.Param.PULSE, 60));
        assertTrue(pulsing.moves());
        assertEquals(0.6f, pulsing.block()[SkyProgram.at(1, SkyProgram.PULSE)], 1e-6f);
        // The stars and the clouds read the twinkle and the pulse from the last value of slot 4, as the bake does.
        assertEquals(4 * 4 + 3, SkyProgram.TWINKLE);
        assertEquals(4 * 4 + 3, SkyProgram.PULSE);
        String glsl = skyLayersSource();
        assertTrue(glsl.contains("float twinkle = albedoLayer(layer, 4).w"));
        assertTrue(glsl.contains("float amount = albedoLayer(layer, 4).w"));
    }

    @Test
    void theTrailsAreInTheBlockOnlyForTheStarsOfASkyThatTurns() throws IOException {
        SkyLayer stars = SkyLayer.of(1, SkyLayer.Kind.STARS).withTransform(new Transform(70, 20, 10, 100));
        SkyLayer galaxy = SkyLayer.of(2, SkyLayer.Kind.GALAXY);
        SkyDesign still = new SkyDesign(Arrays.asList(stars, galaxy), SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED,
                SkyDesign.Extent.SPHERE);
        SkyTurning turning = new SkyTurning(100, 2, SkyTurning.Clock.REAL, 40, SkyTurning.Direction.REVERSE, 30, 45, 70);
        float[] block = SkyProgram.of(still.withTurning(turning), new float[SkyImage.RING * 3]).block();
        assertEquals((float) Math.toRadians(45), block[SkyProgram.at(0, SkyProgram.TRAIL)], 1e-6f);
        assertEquals(0.7f, block[SkyProgram.at(0, SkyProgram.TRAIL_LIGHT)], 1e-6f);
        double[] axis = SkyTurn.trailAxis(turning);
        for (int row = 0; row < 3; row++) {
            double v = 0;
            for (int c = 0; c < 3; c++) v += block[SkyProgram.at(0, SkyProgram.LOCAL + row * 4 + c)] * axis[c];
            assertEquals(v, block[SkyProgram.at(0, SkyProgram.TRAIL_AXIS + row * 4)], 1e-6, "the axis in the layer frame, row " + row);
        }
        assertArrayEquals(SkyProgram.of(still, new float[SkyImage.RING * 3]).block(),
                SkyProgram.of(still.withTurning(turning.withTrails(0)), new float[SkyImage.RING * 3]).block(), "no trails");
        assertArrayEquals(SkyProgram.of(still, new float[SkyImage.RING * 3]).block(),
                SkyProgram.of(still.withTurning(turning.withAmount(0)), new float[SkyImage.RING * 3]).block(), "a still sky has no trails");
        // The stars read the trail arc, the trail light and the axis from the last values of slots 5 to 9, as the bake does.
        assertEquals(5 * 4 + 3, SkyProgram.TRAIL);
        assertEquals(6 * 4 + 3, SkyProgram.TRAIL_LIGHT);
        assertEquals(7 * 4 + 3, SkyProgram.TRAIL_AXIS);
        String glsl = skyLayersSource();
        assertTrue(glsl.contains("vec4 trail = vec4(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w, "
                + "albedoLayer(layer, 5).w);"));
        assertTrue(glsl.contains("seed, trail, albedoLayer(layer, 6).w);"));
    }

    private static SkyProgram program(SkyLayer... layers) {
        SkyDesign d = new SkyDesign(Arrays.asList(layers), SkyDesign.Celestial.HIDE, SkyDesign.Light.FIXED,
                SkyDesign.Extent.SPHERE);
        return SkyProgram.of(d, new float[SkyImage.RING * 3]);
    }

    /** The text of the sky include of the shaders, which the tests compare with the numbers of the bake. */
    static String skyLayersSource() throws IOException {
        try (InputStream in = SkyProgramTest.class.getResourceAsStream("/assets/albedo/shaders/include/sky_layers.glsl")) {
            assertNotNull(in, "the shader is a core resource");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void allPresetsDrawSharp() {
        SkyProgram dusk = SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program();
        assertTrue(dusk.drawsSharp());
        assertEquals(0, dusk.terms());
        SkyProgram nebula = SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program();
        assertTrue(nebula.drawsSharp(), "the blur and the bloom are terms, the hue shift is calculated for each pixel");
        assertEquals(2, nebula.terms());
        assertEquals(0, nebula.termSlot(3), "the blur");
        assertEquals(-1, nebula.termSlot(4), "the hue shift");
        assertEquals(-1, nebula.termSlot(5), "the stars over the blur");
        assertEquals(1, nebula.termSlot(6), "the bloom");
        assertEquals(SkyProgram.KIND_HUE_SHIFT, nebula.block()[SkyProgram.at(4, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_STARS, nebula.block()[SkyProgram.at(5, SkyProgram.KIND)]);
        SkyProgram galaxy = SkyGenerator.bake(SkyPresets.GALAXY, 64, 32).program();
        assertTrue(galaxy.drawsSharp(), "gradient, noise, stars, galaxy and the bloom term");
        assertEquals(SkyProgram.KIND_NOISE, galaxy.block()[SkyProgram.at(1, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_GALAXY, galaxy.block()[SkyProgram.at(3, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_BLOOM, galaxy.block()[SkyProgram.at(4, SkyProgram.KIND)]);
        assertEquals(1, galaxy.terms());
        assertEquals(1, galaxy.block()[2], "the header has the term count");
        assertEquals(-1, galaxy.termSlot(3));
        assertEquals(0, galaxy.termSlot(4));
        SkyProgram aurora = SkyGenerator.bake(SkyPresets.AURORA, 64, 32).program();
        assertTrue(aurora.drawsSharp(), "gradient, glow, stars and aurora");
        assertEquals(0, aurora.terms());
        assertEquals(SkyProgram.KIND_AURORA, aurora.block()[SkyProgram.at(3, SkyProgram.KIND)]);
        assertEquals(0.5f, aurora.block()[SkyProgram.at(3, SkyProgram.SPEED)]);
    }

    @Test
    void theGalaxyValuesAreInTheFreeFloatsOfTheLayer() {
        SkyLayer g = SkyLayer.of(1, SkyLayer.Kind.GALAXY).withParam(SkyLayer.Param.TILT, 45).withParam(SkyLayer.Param.DUST, 30)
                .withParam(SkyLayer.Param.JETS, 80).withParam(SkyLayer.Param.COLOR_3, 0xFFFF0000);
        float[] b = SkyProgram.of(SkyPresets.GALAXY.withLayers(Collections.singletonList(g)), new float[SkyImage.RING * 3]).block();
        assertEquals(0.5f, b[SkyProgram.at(0, SkyProgram.TILT)], 1e-6f);
        assertEquals(0.3f, b[SkyProgram.at(0, SkyProgram.DUST)], 1e-6f);
        assertEquals(0.8f, b[SkyProgram.at(0, SkyProgram.JETS)], 1e-6f);
        assertEquals(1f, b[SkyProgram.at(0, SkyProgram.COLOR_3)], 1e-6f);
        assertEquals(0f, b[SkyProgram.at(0, SkyProgram.COLOR_3 + 4)], 1e-6f);
        // The rotation rows keep their own floats.
        assertEquals(1f, b[SkyProgram.at(0, SkyProgram.LOCAL)] * b[SkyProgram.at(0, SkyProgram.LOCAL)]
                + b[SkyProgram.at(0, SkyProgram.LOCAL + 1)] * b[SkyProgram.at(0, SkyProgram.LOCAL + 1)]
                + b[SkyProgram.at(0, SkyProgram.LOCAL + 2)] * b[SkyProgram.at(0, SkyProgram.LOCAL + 2)], 1e-5f);
        int[] used = {SkyProgram.KIND, SkyProgram.BLEND, SkyProgram.USE, SkyProgram.MASK_INDEX, SkyProgram.MASK_DARK, SkyProgram.OPACITY,
            SkyProgram.AMOUNT, SkyProgram.HEIGHT, SkyProgram.SCALE, SkyProgram.SEED, SkyProgram.ALPHA_1, SkyProgram.ALPHA_2,
            SkyProgram.TERM_SLOT, SkyProgram.TILT, SkyProgram.DUST, SkyProgram.JETS};
        Set<Integer> taken = new HashSet<Integer>();
        for (int at : used) assertTrue(taken.add(at), "offset " + at);
        for (int c = 0; c < 3; c++) {
            for (int base : new int[]{SkyProgram.COLOR_1, SkyProgram.COLOR_2, SkyProgram.LAB_1, SkyProgram.LAB_2}) {
                assertTrue(taken.add(base + c), "offset " + (base + c));
            }
            assertTrue(taken.add(SkyProgram.COLOR_3 + c * 4), "jet color " + c);
            for (int r = 0; r < 3; r++) assertTrue(taken.add(SkyProgram.LOCAL + r * 4 + c), "rotation " + r);
        }
        assertEquals(SkyProgram.LAYER_VEC4S * 4, taken.size(), "each float of the layer has one use");
    }

    @Test
    void theFiltersThatReadOtherPixelsAreTermsAndTheFiltersHaveTheirOwnCodes() {
        Set<Integer> codes = new HashSet<Integer>();
        for (SkyLayer.Kind kind : SkyLayer.Kind.values()) {
            boolean readsOthers = kind == SkyLayer.Kind.BLUR || kind == SkyLayer.Kind.BLOOM || kind == SkyLayer.Kind.PIXELATE
                    || kind == SkyLayer.Kind.CRYSTALLIZE || kind == SkyLayer.Kind.SWIRL;
            assertEquals(readsOthers ? SkyProgram.Path.TERM : SkyProgram.Path.PIXEL, SkyProgram.path(kind), kind.name());
            boolean filter = kind.role() == SkyLayer.Role.FILTER;
            int code = SkyProgram.kindCode(kind);
            assertEquals(filter, code >= SkyProgram.KIND_BLOOM && code <= SkyProgram.KIND_VIGNETTE, kind.name());
            assertTrue(code > 0, kind.name());
            assertTrue(codes.add(code), "each kind has its own code: " + kind.name());
        }
    }

    @Test
    void aWarpTermHoldsTheFullResultOfTheFilter() {
        int w = 128, h = 64;
        List<SkyLayer> layers = new ArrayList<SkyLayer>(SkyPresets.GALAXY.layers().subList(0, 4));
        layers.add(SkyLayer.of(5, SkyLayer.Kind.SWIRL));
        SkyImage img = SkyGenerator.bake(SkyPresets.GALAXY.withLayers(layers), w, h);
        assertEquals(0, img.program().termSlot(4));
        double error = 0;
        int count = 0;
        for (int ty = 0; ty < h / 2; ty++) {
            for (int tx = 0; tx < w / 2; tx++) {
                float sum = 0;
                for (int y = 2 * ty; y <= 2 * ty + 1; y++) {
                    for (int x = 2 * tx; x <= 2 * tx + 1; x++) sum += Oklab.toLinear(img.argb()[y * w + x] >> 8);
                }
                error += Math.abs(sum / 4 - SkyGeneratorTest.fromHalf(img.terms()[(ty * (w / 2) + tx) * 4 + 1]));
                count++;
            }
        }
        assertTrue(error / count < 0.004, "the term is the swirled sky at half size: " + error / count);
    }

    @Test
    void aBloomAfterTheLastTermSlotIsBaked() {
        List<SkyLayer> layers = new ArrayList<SkyLayer>();
        layers.add(SkyLayer.of(1, SkyLayer.Kind.GRADIENT));
        for (int i = 0; i <= SkyProgram.MAX_TERMS; i++) layers.add(SkyLayer.of(i + 2, SkyLayer.Kind.BLOOM));
        SkyProgram p = SkyProgram.of(SkyPresets.GALAXY.withLayers(layers), new float[SkyImage.RING * 3]);
        assertEquals(SkyProgram.MAX_TERMS, p.terms());
        assertEquals(SkyProgram.MAX_TERMS - 1, p.termSlot(SkyProgram.MAX_TERMS));
        assertEquals(SkyProgram.Path.BAKED, p.path(SkyProgram.MAX_TERMS + 1));
        assertEquals(-1, p.termSlot(SkyProgram.MAX_TERMS + 1));
        assertFalse(p.drawsSharp());
    }

    @Test
    void theDuskBlockHasTheLayersTheMaskAndTheHorizonColors() {
        SkyImage img = SkyGenerator.bake(SkyPresets.DUSK, 64, 32);
        float[] b = img.program().block();
        assertEquals(4, b[0], "four layers");
        assertEquals(0, b[1], "the sky stops at the horizon");
        assertEquals(SkyProgram.KIND_GRADIENT, b[SkyProgram.at(0, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_GLOW, b[SkyProgram.at(1, SkyProgram.KIND)]);
        assertEquals(SkyProgram.USE_MASK, b[SkyProgram.at(2, SkyProgram.USE)], "the mask only layer is made for the stars");
        assertEquals(SkyProgram.KIND_STARS, b[SkyProgram.at(3, SkyProgram.KIND)]);
        assertEquals(2, b[SkyProgram.at(3, SkyProgram.MASK_INDEX)], "the stars use the third layer as their mask");
        assertEquals(1, b[SkyProgram.at(3, SkyProgram.MASK_DARK)]);
        assertEquals(SkyProgram.BLEND_ADD, b[SkyProgram.at(3, SkyProgram.BLEND)]);
        assertEquals(0.75f, b[SkyProgram.at(3, SkyProgram.OPACITY)], 1e-6f);
        assertEquals(-1, b[SkyProgram.at(0, SkyProgram.MASK_INDEX)]);
        assertEquals(1f, b[SkyProgram.at(0, SkyProgram.LOCAL)], 1e-6f, "an identity rotation");
        float[] fog = img.fogRgb(180, 1);
        float north = b[SkyProgram.RING];
        assertTrue(north > 0, "the horizon colors follow the layers");
        assertTrue(Math.abs(Oklab.toSrgb(north) / 255f - fog[0]) < 0.05f, "the north fog color is near the first sector");
    }
}
