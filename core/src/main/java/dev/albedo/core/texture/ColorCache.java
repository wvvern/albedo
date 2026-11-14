package dev.albedo.core.texture;

/**
 * Colors seen in one image and their results, because textures have few distinct colors, with room for twice the
 * colors it keeps so that a lookup always reaches an empty slot soon.
 */
final class ColorCache {
    private static final int MAX_SIZE = 1 << 13;
    private final int[] keys, values;
    private final boolean[] used;
    private final int mask;
    private int count;
    /** The result of the last color that did not fit, because the cache keeps at most half its size. */
    private int spare;

    ColorCache(int pixels) {
        int size = Integer.highestOneBit(Math.max(4, Math.min(MAX_SIZE, pixels * 2) - 1)) << 1;
        keys = new int[size];
        values = new int[size];
        used = new boolean[size];
        mask = size - 1;
    }

    /** The slot of the color, or the empty slot where it belongs. */
    int slot(int argb) {
        int i = (argb * 0x9E3779B9 >>> 16) & mask;
        while (used[i] && keys[i] != argb) i = (i + 1) & mask;
        return i;
    }

    boolean has(int slot) {
        return used[slot];
    }

    void store(int slot, int argb, int result) {
        if (count * 2 >= keys.length) {
            spare = result;
            return;
        }
        used[slot] = true;
        keys[slot] = argb;
        values[slot] = result;
        count++;
    }

    int value(int slot, int argb) {
        return used[slot] && keys[slot] == argb ? values[slot] : spare;
    }
}
