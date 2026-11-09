package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;

import java.util.List;

/**
 * The numbers of a {@link SkyDesign} in one block of floats that the CPU bake and the sky shader both read, laid out as a
 * std140 uniform block of vec4 values: a header, {@link SkyDesign#MAX_LAYERS} layers of {@link #LAYER_VEC4S} each, and
 * the {@link SkyImage#RING} horizon colors.
 */
public final class SkyProgram {
    /** Where the GPU gets the pixels of a layer. */
    public enum Path {
        /** The sky shader computes the layer for each screen pixel. */
        PIXEL,
        /** Only the CPU bake makes the layer, so the sky draws from the baked image. */
        TERM
    }

    /** The vec4 values of each layer. */
    public static final int LAYER_VEC4S = 10;
    /** The float index of the first layer. */
    static final int LAYERS = 4;
    /** The float index of the first horizon color. */
    static final int RING = LAYERS + SkyDesign.MAX_LAYERS * LAYER_VEC4S * 4;
    /** The number of floats in the block. */
    public static final int FLOATS = RING + SkyImage.RING * 4;

    /** The kind codes of the shader, where 0 draws nothing. */
    static final int KIND_GRADIENT = 1, KIND_GLOW = 2, KIND_STARS = 3, KIND_NOISE = 4, KIND_FRACTAL = 5, KIND_GALAXY = 6;
    /** The blend codes of the shader. */
    static final int BLEND_NORMAL = 0, BLEND_ADD = 1, BLEND_SCREEN = 2, BLEND_MULTIPLY = 3;
    /** What the shader does with a layer: skip it, make it only for the masks of higher layers, or show it. */
    static final int USE_NONE = 0, USE_MASK = 1, USE_SHOW = 2;

    // The float offsets in a layer.
    static final int KIND = 0, BLEND = 1, USE = 2, MASK_INDEX = 3, MASK_DARK = 4, OPACITY = 5, AMOUNT = 6, HEIGHT = 7;
    static final int SCALE = 8, SEED = 9, ALPHA_1 = 10, ALPHA_2 = 11, COLOR_1 = 12, COLOR_2 = 16, LAB_1 = 20, LAB_2 = 24;
    /** The three rows of the matrix from sky directions to the layer frame, each a vec4. */
    static final int LOCAL = 28;

    private final float[] block = new float[FLOATS];
    private final Path[] paths;

    private SkyProgram(SkyDesign design, float[] ring) {
        List<SkyLayer> layers = design.layers();
        block[0] = layers.size();
        block[1] = design.extent() == SkyDesign.Extent.SPHERE ? 1 : 0;
        paths = new Path[layers.size()];
        boolean[] maskSource = new boolean[layers.size()];
        for (int i = 0; i < layers.size(); i++) {
            SkyMask m = layers.get(i).mask();
            int source = m.isNone() ? -1 : design.indexOf(m.layer());
            if (source >= 0 && source < i) maskSource[source] = true;
        }
        for (int i = 0; i < layers.size(); i++) {
            SkyLayer l = layers.get(i);
            paths[i] = path(l.kind());
            int base = LAYERS + i * LAYER_VEC4S * 4;
            block[base + KIND] = kindCode(l.kind());
            block[base + BLEND] = blendCode(l.blend());
            block[base + USE] = l.output() == SkyLayer.Output.SHOW ? USE_SHOW : maskSource[i] ? USE_MASK : USE_NONE;
            int source = l.mask().isNone() ? -1 : design.indexOf(l.mask().layer());
            block[base + MASK_INDEX] = source < i ? source : -1;
            block[base + MASK_DARK] = l.mask().mode() == SkyMask.Mode.DARK ? 1 : 0;
            block[base + OPACITY] = l.opacity() / 100f;
            block[base + AMOUNT] = l.param(SkyLayer.Param.AMOUNT) / 100f;
            block[base + HEIGHT] = l.param(SkyLayer.Param.HEIGHT) / 100f;
            block[base + SCALE] = l.transform().scale() / 100f;
            block[base + SEED] = l.param(SkyLayer.Param.SEED);
            color(l.param(SkyLayer.Param.COLOR_1), base + ALPHA_1, base + COLOR_1, base + LAB_1);
            color(l.param(SkyLayer.Param.COLOR_2), base + ALPHA_2, base + COLOR_2, base + LAB_2);
            double[] r = l.transform().rotation();
            for (int row = 0; row < 3; row++) {
                for (int c = 0; c < 3; c++) block[base + LOCAL + row * 4 + c] = (float) r[c * 3 + row];
            }
        }
        for (int s = 0; s < SkyImage.RING; s++) System.arraycopy(ring, s * 3, block, RING + s * 4, 3);
    }

    /**
     * The program of a design.
     *
     * @param ring the linear {r, g, b} of each horizon sector, from the bake
     */
    static SkyProgram of(SkyDesign design, float[] ring) {
        return new SkyProgram(design, ring);
    }

    /** The path of a kind: the kinds that the shader does not draw are baked on the CPU. */
    public static Path path(SkyLayer.Kind kind) {
        switch (kind) {
            case GRADIENT:
            case GLOW:
            case NOISE:
            case FRACTAL:
            case GALAXY:
            case STARS:
                return Path.PIXEL;
            default:
                return Path.TERM;
        }
    }

    /** The block, which the caller must not change. */
    public float[] block() { return block; }

    /** The path of each layer from the bottom up. */
    public Path path(int layer) { return paths[layer]; }

    /** True when the shader draws all layers, so the sky can draw sharp with no baked image. */
    public boolean allPixel() {
        for (Path p : paths) if (p != Path.PIXEL) return false;
        return true;
    }

    /** The float index of a value of a layer. */
    static int at(int layer, int offset) {
        return LAYERS + layer * LAYER_VEC4S * 4 + offset;
    }

    private void color(int argb, int alpha, int linear, int lab) {
        block[alpha] = (argb >>> 24) / 255f;
        float[] rgb = Oklab.linear(argb);
        System.arraycopy(rgb, 0, block, linear, 3);
        float[] out = new float[3];
        Oklab.fromLinear(rgb[0], rgb[1], rgb[2], out);
        System.arraycopy(out, 0, block, lab, 3);
    }

    private static int kindCode(SkyLayer.Kind kind) {
        switch (kind) {
            case GRADIENT: return KIND_GRADIENT;
            case GLOW: return KIND_GLOW;
            case STARS: return KIND_STARS;
            case NOISE: return KIND_NOISE;
            case FRACTAL: return KIND_FRACTAL;
            case GALAXY: return KIND_GALAXY;
            default: return 0;
        }
    }

    private static int blendCode(SkyLayer.Blend blend) {
        switch (blend) {
            case ADD: return BLEND_ADD;
            case SCREEN: return BLEND_SCREEN;
            case MULTIPLY: return BLEND_MULTIPLY;
            default: return BLEND_NORMAL;
        }
    }
}
