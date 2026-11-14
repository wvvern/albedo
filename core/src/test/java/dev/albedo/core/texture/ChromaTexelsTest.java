package dev.albedo.core.texture;

import dev.albedo.core.config.Chroma;
import dev.albedo.core.shader.ShaderSource;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

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
        String include = ShaderSource.read("include/chroma.glsl");
        assertTrue(include.contains("#define ALBEDO_CHROMA_MAX_AMOUNT " + ChromaTexels.MAX_AMOUNT), include);
        assertTrue(include.contains("#define ALBEDO_CHROMA_VEC4S " + Chroma.TURN_FLOATS / 4 + "\n"), include);
    }

    @Test
    void rgbaPutsTheBytesOfEachTexelInTheOrderOfAnRgba8Texture() {
        ByteBuffer out = ByteBuffer.allocate(8);
        ChromaTexels.rgba(new int[]{0x44112233, 0x88AABBCC}, out);
        assertEquals(0, out.position(), "the buffer is ready to read");
        byte[] bytes = new byte[8];
        out.get(bytes);
        assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0x88}, bytes);
    }

    @Test
    void theMipLevelsStopAtALevelOfOneTexel() {
        assertEquals(12, ChromaTexels.mipLevels(2048, 1024, 1001), "the default largest level of 1000");
        assertEquals(1, ChromaTexels.mipLevels(0, 0, 1001));
        assertEquals(4, ChromaTexels.mipLevels(16, 16, 4));
        assertEquals(1, ChromaTexels.mipLevels(16, 16, 0));
        assertEquals(256, ChromaTexels.levelSize(2048, 3));
        assertEquals(1, ChromaTexels.levelSize(2, 5), "a level is never below one texel");
    }

    @Test
    void anAtlasWithNoMovingSpriteKeepsNoLevel() {
        ChromaTexels.Atlas atlas = new ChromaTexels.Atlas(4096, 4096, (location, w, h, out) -> false);
        atlas.sprite("minecraft:block/not_kept", 0, 0, 16, 16, 0);
        assertFalse(atlas.moving());
    }

    /** A source whose one sprite has these texels in one frame. */
    private static ChromaTexels.Source frame(int... texels) {
        return (location, w, h, out) -> {
            System.arraycopy(texels, 0, out, 0, texels.length);
            return true;
        };
    }

    @Test
    void theBorderOfASpriteRepeatsItsEdgeTexels() {
        int a = ChromaTexels.pack(10, 0.1, 0.1), b = ChromaTexels.pack(20, 0.6, 0.2);
        ChromaTexels.Atlas atlas = new ChromaTexels.Atlas(4, 3, frame(a, b));
        atlas.sprite("minecraft:block/moving", 0, 0, 2, 1, 1);
        assertTrue(atlas.moving());
        int[][] levels = atlas.levels(2);
        assertArrayEquals(new int[]{a, a, b, b,
                                    a, a, b, b,
                                    a, a, b, b}, levels[0]);
        assertArrayEquals(ChromaTexels.halve(levels[0], 4, 3), levels[1], "each level halves the level before it");
    }

    @Test
    void aSpriteAtTheEdgeOfTheAtlasIsClipped() {
        int a = ChromaTexels.pack(10, 0.1, 0.1), b = ChromaTexels.pack(20, 0.6, 0.2);
        ChromaTexels.Atlas atlas = new ChromaTexels.Atlas(3, 2, frame(a, b, b, b));
        atlas.sprite("minecraft:block/moving", 2, 1, 2, 2, 0);
        assertArrayEquals(new int[]{0, 0, 0,
                                    0, 0, a}, atlas.levels(1)[0]);
    }
}
