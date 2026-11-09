package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        String glsl;
        try (InputStream in = SkyProgramTest.class.getResourceAsStream("/assets/albedo/shaders/include/sky_layers.glsl")) {
            assertNotNull(in, "the shader is a core resource");
            glsl = new String(readAll(in), StandardCharsets.UTF_8);
        }
        String[] defines = {"MAX_LAYERS " + SkyDesign.MAX_LAYERS, "LAYER_VEC4S " + SkyProgram.LAYER_VEC4S, "RING " + SkyImage.RING,
            "KIND_GRADIENT " + SkyProgram.KIND_GRADIENT, "KIND_GLOW " + SkyProgram.KIND_GLOW, "KIND_STARS " + SkyProgram.KIND_STARS,
            "KIND_NOISE " + SkyProgram.KIND_NOISE, "KIND_FRACTAL " + SkyProgram.KIND_FRACTAL, "KIND_GALAXY " + SkyProgram.KIND_GALAXY,
            "KIND_BLOOM " + SkyProgram.KIND_BLOOM, "KIND_BLUR " + SkyProgram.KIND_BLUR, "KIND_PIXELATE " + SkyProgram.KIND_PIXELATE,
            "KIND_CRYSTALLIZE " + SkyProgram.KIND_CRYSTALLIZE, "KIND_SWIRL " + SkyProgram.KIND_SWIRL,
            "KIND_POSTERIZE " + SkyProgram.KIND_POSTERIZE, "KIND_HUE_SHIFT " + SkyProgram.KIND_HUE_SHIFT,
            "KIND_VIGNETTE " + SkyProgram.KIND_VIGNETTE,
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
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
        return out.toByteArray();
    }

    @Test
    void allPresetsDrawSharp() {
        SkyProgram dusk = SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program();
        assertTrue(dusk.drawsSharp());
        assertEquals(0, dusk.terms());
        SkyProgram nebula = SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program();
        assertTrue(nebula.drawsSharp(), "the blur and the bloom are terms, the hue shift is calculated for each pixel");
        assertEquals(2, nebula.terms());
        assertEquals(0, nebula.termSlot(4), "the blur");
        assertEquals(-1, nebula.termSlot(5), "the hue shift");
        assertEquals(1, nebula.termSlot(6), "the bloom");
        assertEquals(SkyProgram.KIND_HUE_SHIFT, nebula.block()[SkyProgram.at(5, SkyProgram.KIND)]);
        SkyProgram galaxy = SkyGenerator.bake(SkyPresets.GALAXY, 64, 32).program();
        assertTrue(galaxy.drawsSharp(), "gradient, noise, stars, galaxy and the bloom term");
        assertEquals(SkyProgram.KIND_NOISE, galaxy.block()[SkyProgram.at(1, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_GALAXY, galaxy.block()[SkyProgram.at(3, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_BLOOM, galaxy.block()[SkyProgram.at(4, SkyProgram.KIND)]);
        assertEquals(1, galaxy.terms());
        assertEquals(1, galaxy.block()[2], "the header has the term count");
        assertEquals(-1, galaxy.termSlot(3));
        assertEquals(0, galaxy.termSlot(4));
    }

    @Test
    void theFiltersThatReadOtherPixelsAreTermsAndTheFiltersHaveTheHighCodes() {
        for (SkyLayer.Kind kind : SkyLayer.Kind.values()) {
            boolean readsOthers = kind == SkyLayer.Kind.BLUR || kind == SkyLayer.Kind.BLOOM || kind == SkyLayer.Kind.PIXELATE
                    || kind == SkyLayer.Kind.CRYSTALLIZE || kind == SkyLayer.Kind.SWIRL;
            assertEquals(readsOthers ? SkyProgram.Path.TERM : SkyProgram.Path.PIXEL, SkyProgram.path(kind), kind.name());
            boolean filter = kind.role() == SkyLayer.Role.FILTER;
            assertEquals(filter, SkyProgram.kindCode(kind) >= SkyProgram.KIND_BLOOM, kind.name());
            assertTrue(SkyProgram.kindCode(kind) > 0, kind.name());
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
            // The rows near the horizon go into the horizon color after the layers.
            if (Math.abs(90 - (2 * ty + 1) * 180.0 / h) < 6) continue;
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
        assertEquals(0.55f, b[SkyProgram.at(3, SkyProgram.OPACITY)], 1e-6f);
        assertEquals(-1, b[SkyProgram.at(0, SkyProgram.MASK_INDEX)]);
        assertEquals(1f, b[SkyProgram.at(0, SkyProgram.LOCAL)], 1e-6f, "an identity rotation");
        float[] fog = img.fogRgb(180, 1);
        float north = b[SkyProgram.RING];
        assertTrue(north > 0, "the horizon colors follow the layers");
        assertTrue(Math.abs(Oklab.toSrgb(north) / 255f - fog[0]) < 0.05f, "the north fog color is near the first sector");
    }
}
