package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyProgramTest {
    @Test
    void theBlockIsAWholeNumberOfVec4Values() {
        assertEquals(0, SkyProgram.FLOATS % 4);
        assertEquals(4 + SkyDesign.MAX_LAYERS * SkyProgram.LAYER_VEC4S * 4 + SkyImage.RING * 4, SkyProgram.FLOATS);
        assertEquals(SkyProgram.LAYER_VEC4S * 4, SkyProgram.LOCAL + 12, "the rotation rows end the layer");
    }

    @Test
    void duskDrawsSharpAndTheSkiesWithFiltersOrNoiseDoNot() {
        SkyProgram dusk = SkyGenerator.bake(SkyPresets.DUSK, 64, 32).program();
        assertTrue(dusk.allPixel());
        assertFalse(SkyGenerator.bake(SkyPresets.NEBULA, 64, 32).program().allPixel());
        assertFalse(SkyGenerator.bake(SkyPresets.GALAXY, 64, 32).program().allPixel());
        assertEquals(SkyProgram.Path.TERM, SkyProgram.path(SkyLayer.Kind.BLUR));
        assertEquals(SkyProgram.Path.PIXEL, SkyProgram.path(SkyLayer.Kind.STARS));
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
