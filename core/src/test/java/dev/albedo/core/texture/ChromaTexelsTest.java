package dev.albedo.core.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void theNextMipLevelAddsTheMeanHueOfAllFourTexels() {
        int line = ChromaTexels.pack(30, 0.25, 0.2);
        int[] half = ChromaTexels.halve(new int[]{line, 0, 0, line}, 2, 2);
        assertEquals(30, ChromaTexels.speed(half[0]));
        assertEquals(0.25, ChromaTexels.hue(half[0]), 1.0 / 0x10000);
        assertEquals(0.1, ChromaTexels.amount(half[0]), 0.002, "two still texels add no hue, which halves the mean");
        int opposite = ChromaTexels.pack(30, 0.75, 0.2);
        half = ChromaTexels.halve(new int[]{line, opposite, line, opposite}, 2, 2);
        assertEquals(0, ChromaTexels.amount(half[0]), 0.002, "opposite hues cancel");
    }

    @Test
    void theShaderUsesTheSameMaximumAmount() throws Exception {
        String include = dev.albedo.core.sky.SkyShaderSource.read("include/chroma.glsl");
        assertTrue(include.contains("#define ALBEDO_CHROMA_MAX_AMOUNT " + ChromaTexels.MAX_AMOUNT), include);
        assertTrue(include.contains("#define ALBEDO_CHROMA_VEC4S " + dev.albedo.core.config.Chroma.TURN_FLOATS / 4 + "\n"), include);
    }

    @Test
    void rgbaPutsTheBytesOfEachTexelInTheOrderOfAnRgba8Texture() {
        java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(8);
        ChromaTexels.rgba(new int[]{0x44112233, 0x88AABBCC}, out);
        assertEquals(0, out.position(), "the buffer is ready to read");
        byte[] bytes = new byte[8];
        out.get(bytes);
        assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0x88}, bytes);
    }

    @Test
    void anAtlasWithNoMovingSpriteKeepsNoLevel() {
        ChromaTexels.Atlas atlas = new ChromaTexels.Atlas(4096, 4096);
        atlas.sprite("minecraft:block/not_kept", 0, 0, 16, 16, 0);
        assertFalse(atlas.moving());
    }
}
