package dev.albedo.legacy;

/**
 * The face shade that 1.8.9 FaceBakery bakes into the vertex color of block quads (0.5 down, 0.6 east and west, 0.8
 * north and south of 255, white for up and unshaded faces), which the chunk mesh gets as white when shading is off.
 */
final class FaceShade {
    /** The index of the color in each vertex of the vertex data (4 vertices of 7 ints, 14 with OptiFine shaders). */
    private static final int COLOR = 3;
    private static final ThreadLocal<int[]> SCRATCH = new ThreadLocal<int[]>();

    private FaceShade() {}

    /** @return {@code data}, or a copy with white in place of the face shade (the quad itself does not change) */
    static int[] remove(int[] data) {
        int n = data.length;
        if (n == 0 || n % 4 != 0 || n / 4 <= COLOR) return data;
        int stride = n / 4;
        int color = data[COLOR];
        if (!isShade(color)) return data;
        for (int v = 1; v < 4; v++) if (data[v * stride + COLOR] != color) return data;
        int[] out = SCRATCH.get(); // addVertexData copies it into the buffer at once, so each thread can use one array
        if (out == null || out.length != n) {
            out = new int[n];
            SCRATCH.set(out);
        }
        System.arraycopy(data, 0, out, 0, n);
        for (int v = 0; v < 4; v++) out[v * stride + COLOR] = -1;
        return out;
    }

    /** One vertex color as 0..1 floats (Forge's lighting pipeline). */
    static void remove(float[] rgba) {
        if (rgba.length < 3 || rgba[0] != rgba[1] || rgba[1] != rgba[2] || (rgba.length > 3 && rgba[3] != 1f)) return;
        if (isGray(Math.round(rgba[0] * 255f))) {
            rgba[0] = 1f;
            rgba[1] = 1f;
            rgba[2] = 1f;
        }
    }

    /** Opaque, the same value in the 3 color bytes (so the byte order is not important), and one of the grays. */
    private static boolean isShade(int color) {
        int g = color & 0xFF;
        return (color >>> 24) == 0xFF && (color >> 8 & 0xFF) == g && (color >> 16 & 0xFF) == g && isGray(g);
    }

    private static boolean isGray(int g) {
        return g == 127 || g == 153 || g == 204;
    }
}
