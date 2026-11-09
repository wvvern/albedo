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
            "KIND_BLOOM " + SkyProgram.KIND_BLOOM,
            "BLEND_NORMAL " + SkyProgram.BLEND_NORMAL, "BLEND_ADD " + SkyProgram.BLEND_ADD, "BLEND_SCREEN " + SkyProgram.BLEND_SCREEN,
            "BLEND_MULTIPLY " + SkyProgram.BLEND_MULTIPLY, "USE_NONE " + SkyProgram.USE_NONE, "USE_MASK " + SkyProgram.USE_MASK,
            "USE_SHOW " + SkyProgram.USE_SHOW};
        for (String d : defines) assertTrue(glsl.contains("#define ALBEDO_" + d + "\n"), d);
        // The shader reads the values of a layer from these vec4 slots.
        assertEquals(4, SkyProgram.MASK_DARK);
        assertEquals(8, SkyProgram.SCALE);
        assertEquals(12, SkyProgram.COLOR_1);
        assertEquals(15, SkyProgram.TERM_SLOT);
        assertTrue(glsl.contains("int(albedoLayer(i, 3).w)"), "the term slot is the last value of slot 3");
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
    void theGeneratorsAndTheBloomDrawSharpAndTheOtherFiltersDoNot() {
        SkyProgram dusk = SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program();
        assertTrue(dusk.drawsSharp());
        assertEquals(0, dusk.terms());
        assertFalse(SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program().drawsSharp());
        SkyProgram galaxy = SkyGenerator.bake(SkyPresets.GALAXY, 64, 32).program();
        assertTrue(galaxy.drawsSharp(), "gradient, noise, stars, galaxy and the bloom term");
        assertEquals(SkyProgram.KIND_NOISE, galaxy.block()[SkyProgram.at(1, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_GALAXY, galaxy.block()[SkyProgram.at(3, SkyProgram.KIND)]);
        assertEquals(SkyProgram.KIND_BLOOM, galaxy.block()[SkyProgram.at(4, SkyProgram.KIND)]);
        assertEquals(1, galaxy.terms());
        assertEquals(1, galaxy.block()[2], "the header has the term count");
        assertEquals(-1, galaxy.termSlot(3));
        assertEquals(0, galaxy.termSlot(4));
        for (SkyLayer.Kind kind : SkyLayer.Kind.values()) {
            SkyProgram.Path expected = kind.role() == SkyLayer.Role.GENERATOR ? SkyProgram.Path.PIXEL
                    : kind == SkyLayer.Kind.BLOOM ? SkyProgram.Path.TERM : SkyProgram.Path.BAKED;
            assertEquals(expected, SkyProgram.path(kind), kind.name());
        }
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
