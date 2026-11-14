package dev.albedo.core.dev;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class GlReadbackTest {
    /** A 2x2 image from the top row down: red, green, then blue, white. */
    private static final int[] TOP_DOWN = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};

    @Test
    void theIntReadFlipsTheRowsAndIsOpaque() {
        IntBuffer bottomUp = IntBuffer.wrap(new int[]{0x000000FF, 0x80FFFFFF, 0x00FF0000, 0x0000FF00});
        assertArrayEquals(TOP_DOWN, GlReadback.argbTopDown(bottomUp, 2, 2));
    }

    @Test
    void theByteReadPacksRgbaFlipsTheRowsAndIsOpaque() {
        byte f = (byte) 255;
        ByteBuffer bottomUp = ByteBuffer.wrap(new byte[]{0, 0, f, 0, f, f, f, 7, f, 0, 0, 0, 0, f, 0, (byte) 128});
        assertArrayEquals(TOP_DOWN, GlReadback.argbTopDown(bottomUp, 2, 2));
    }
}
