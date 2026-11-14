package dev.albedo.core.texture;

import dev.albedo.core.Albedo;

import java.nio.ByteBuffer;

/**
 * The texels that tell the GPU pass of Chroma how to turn the colors of an atlas in each frame: the speed, the hue of
 * the kind of texture and the chroma to add, as ARGB values that an adapter uploads as RGBA8.
 */
public final class ChromaTexels {
    /** The OKLab chroma of an amount of 255, which the shader uses too. */
    public static final double MAX_AMOUNT = Effects.MAX_CHROMA;

    private ChromaTexels() {}

    /** The GPU memory that the pass adds for one level of an atlas: the texel texture and the pristine copy, 4 bytes for each texel. */
    public static long levelBytes(int width, int height) {
        return 2L * 4 * width * height;
    }

    /** Puts the texels of one level into {@code out} as RGBA8 bytes from its position, and makes {@code out} ready to read. */
    public static void rgba(int[] texels, ByteBuffer out) {
        for (int c : texels) out.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
        out.flip();
    }

    /** Puts the texels of the sprites of one atlas into one image of the size of the atlas, for the GPU pass. */
    public static final class Atlas {
        private final int width, height;
        /** The texels of the base level, which the first moving sprite makes, so that an atlas with no moving sprite needs no memory. */
        private int[] level;
        /** The texels of one frame of the last sprite, which the next sprite uses again when it is large enough. */
        private int[] frame = new int[0];

        public Atlas(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * Adds the texels of a sprite when its colors move on the GPU.
         *
         * @param location the sprite name with its folder, as the adapter gave it to {@code Albedo.processTexture}
         * @param x the left edge of the sprite in the atlas, its border included
         * @param y the top edge of the sprite in the atlas, its border included
         * @param w the width of one frame of the sprite
         * @param h the height of one frame of the sprite
         * @param border the width of the border of repeated edge pixels around the sprite in the atlas
         */
        public void sprite(String location, int x, int y, int w, int h, int border) {
            if (w <= 0 || h <= 0) return;
            if (frame.length < w * h) frame = new int[w * h];
            if (!Albedo.chromaTexels(location, w, h, frame)) return;
            if (level == null) level = new int[width * height];
            for (int dy = 0; dy < h + 2 * border && y + dy < height; dy++) {
                int row = Math.max(0, Math.min(h - 1, dy - border)) * w, at = (y + dy) * width + x;
                for (int dx = 0; dx < w + 2 * border && x + dx < width; dx++) {
                    level[at + dx] = frame[row + Math.max(0, Math.min(w - 1, dx - border))];
                }
            }
        }

        /** Whether a sprite has moving colors, which the pass needs a GPU target for. */
        public boolean moving() {
            return level != null;
        }

        /** The texels of each mip level of an atlas with a moving sprite, from the size of the atlas down to {@code count} levels. */
        public int[][] levels(int count) {
            if (level == null) throw new IllegalStateException("the atlas has no moving sprite");
            int[][] out = new int[count][];
            out[0] = level;
            for (int i = 1; i < count; i++) out[i] = halve(out[i - 1], Math.max(1, width >> (i - 1)), Math.max(1, height >> (i - 1)));
            return out;
        }
    }

    /**
     * One texel: the speed in alpha, the hue as 16 bits in red and green, and the amount in blue.
     *
     * @param hueTurns the hue as a part of one turn
     * @param amount the OKLab chroma to add, from 0 to {@link #MAX_AMOUNT}
     */
    static int pack(int speed, double hueTurns, double amount) {
        int hue = (int) Math.round((hueTurns - Math.floor(hueTurns)) * 0x10000) & 0xFFFF;
        int bits = (int) Math.round(Math.max(0, Math.min(1, amount / MAX_AMOUNT)) * 255);
        return Math.max(0, Math.min(255, speed)) << 24 | hue << 8 | bits;
    }

    /** The speed of a texel, where 0 means that the pass leaves the pixel as it is. */
    public static int speed(int texel) {
        return texel >>> 24;
    }

    /** The hue of a texel as a part of one turn. */
    public static double hue(int texel) {
        return ((texel >>> 8) & 0xFFFF) / (double) 0x10000;
    }

    /** The OKLab chroma that the pass adds to a texel. */
    public static double amount(int texel) {
        return (texel & 0xFF) / 255.0 * MAX_AMOUNT;
    }

    /**
     * The texels of the next mip level, half as wide and high, because the pass turns each mip level of the atlas on its
     * own. Each gets the speed of the first moving texel of its 2 by 2 block and the mean of the hue the four texels add,
     * where a texel of speed 0 adds none, so that the hue matches a mip level of the painted colors.
     */
    public static int[] halve(int[] texels, int w, int h) {
        int nw = Math.max(1, w >> 1), nh = Math.max(1, h >> 1);
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int first = 0;
                double a = 0, b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int t = texels[Math.min(h - 1, 2 * y + dy) * w + Math.min(w - 1, 2 * x + dx)];
                        if (speed(t) == 0) continue;
                        if (first == 0) first = t;
                        double turn = 2 * Math.PI * hue(t);
                        a += amount(t) * Math.cos(turn);
                        b += amount(t) * Math.sin(turn);
                    }
                }
                out[y * nw + x] = first == 0 ? 0 : pack(speed(first), Math.atan2(b, a) / (2 * Math.PI), Math.hypot(a, b) / 4);
            }
        }
        return out;
    }
}
