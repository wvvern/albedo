package dev.albedo.core.dev;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Turns the pixels of a glReadPixels call, which gives the bottom row first, into opaque ARGB rows from the top. */
public final class GlReadback {
    private GlReadback() {}

    /** The pixels of a read as GL_BGRA and GL_UNSIGNED_INT_8_8_8_8_REV, which is ARGB in each int. */
    public static int[] argbTopDown(IntBuffer bgraBottomUp, int width, int height) {
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = (height - 1 - y) * width;
            for (int x = 0; x < width; x++) argb[y * width + x] = bgraBottomUp.get(row + x) | 0xFF000000;
        }
        return argb;
    }

    /** The pixels of a read as GL_RGBA and GL_UNSIGNED_BYTE. */
    public static int[] argbTopDown(ByteBuffer rgbaBottomUp, int width, int height) {
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++) {
                int at = row + x * 4;
                argb[y * width + x] = 0xFF000000 | (rgbaBottomUp.get(at) & 0xFF) << 16 | (rgbaBottomUp.get(at + 1) & 0xFF) << 8
                        | (rgbaBottomUp.get(at + 2) & 0xFF);
            }
        }
        return argb;
    }
}
