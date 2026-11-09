package dev.albedo.core.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaTexelsTest {
    @Test
    void aTexelKeepsItsSpeedHueAndAmount() {
        int t = ChromaTexels.pack(37, 0.7, 0.13);
        assertEquals(37, ChromaTexels.speed(t));
        assertEquals(0.7, ChromaTexels.hue(t), 1.0 / 0x10000);
        assertEquals(0.13, ChromaTexels.amount(t), ChromaTexels.MAX_AMOUNT / 255);
        assertEquals(0.25, ChromaTexels.hue(ChromaTexels.pack(1, 1.25, 0)), 1.0 / 0x10000, "a hue past one turn wraps");
        assertEquals(ChromaTexels.MAX_AMOUNT, ChromaTexels.amount(ChromaTexels.pack(1, 0, 1)), 1e-9, "the amount stops at its maximum");
    }

    @Test
    void theNextMipLevelKeepsTheHueAndTakesTheMeanAmount() {
        int a = ChromaTexels.pack(30, 0.4, 0.2), b = ChromaTexels.pack(30, 0.4, 0.1);
        int[] level = {a, b, 0, 0,
                       b, a, 0, 0};
        int[] half = ChromaTexels.halve(level, 4, 2);
        assertEquals(2, half.length);
        assertEquals(30, ChromaTexels.speed(half[0]));
        assertEquals(0.4, ChromaTexels.hue(half[0]), 1.0 / 0x10000);
        assertEquals(0.15, ChromaTexels.amount(half[0]), 0.002);
        assertEquals(0, half[1], "a block with no moving texel stays still");
        int[] one = ChromaTexels.halve(new int[]{a}, 1, 1);
        assertEquals(a, one[0], "a level of one texel stays one texel");
    }

    @Test
    void theShaderUsesTheSameMaximumAmount() throws Exception {
        String include = dev.albedo.core.sky.SkyShaderSource.read("include/chroma.glsl");
        assertTrue(include.contains("#define ALBEDO_CHROMA_MAX_AMOUNT " + ChromaTexels.MAX_AMOUNT), include);
        assertTrue(include.contains("#define ALBEDO_CHROMA_VEC4S " + (dev.albedo.core.config.Chroma.MAX_SPEED / 4 + 1)), include);
    }
}
