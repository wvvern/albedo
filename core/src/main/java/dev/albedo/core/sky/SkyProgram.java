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
        /** The CPU bake makes a soft term image of the layer, which the sky shader mixes in for each screen pixel. */
        TERM,
        /** Only the CPU bake makes the layer, because the design has no free term slot, so the sky draws from the baked image. */
        BAKED
    }

    /** The largest number of term images of one design. */
    public static final int MAX_TERMS = 4;
    /** The vec4 values of each layer. */
    public static final int LAYER_VEC4S = 10;
    /** The float index of the first layer, after the header vec4. */
    public static final int LAYERS = 4;
    /** The float index of the first horizon color. */
    public static final int RING = LAYERS + SkyDesign.MAX_LAYERS * LAYER_VEC4S * 4;
    /** The number of floats in the block. */
    public static final int FLOATS = RING + SkyImage.RING * 4;
    /** The float index of the time in seconds, the last value of the header, which each adapter writes for each frame. */
    public static final int TIME = 3;
    /** The time of a still sky, in which the layers that move draw nothing, as in the CPU bake. */
    public static final float STILL = -1f;

    /** The kind codes of the generators in the shader. */
    static final int KIND_GRADIENT = 1, KIND_GLOW = 2, KIND_STARS = 3, KIND_NOISE = 4, KIND_FRACTAL = 5, KIND_GALAXY = 6;
    /** The kind code of a bloom, whose term image holds only the glow that it adds. */
    static final int KIND_BLOOM = 7;
    /** The kind codes of the filters whose term image holds their full result. */
    static final int KIND_BLUR = 8, KIND_PIXELATE = 9, KIND_CRYSTALLIZE = 10, KIND_SWIRL = 11;
    /** The kind codes of the filters that the shader calculates for each pixel from the sky below them. */
    static final int KIND_POSTERIZE = 12, KIND_HUE_SHIFT = 13, KIND_VIGNETTE = 14;
    /** The kind code of the meteors, a generator that only the shader draws, because it moves with the time. */
    static final int KIND_METEORS = 15;
    /** The kind code of the aurora, a generator that moves with the time and that the bake draws at time 0. */
    static final int KIND_AURORA = 16;
    /** The blend codes of the shader. */
    static final int BLEND_NORMAL = 0, BLEND_ADD = 1, BLEND_SCREEN = 2, BLEND_MULTIPLY = 3;
    /** What the shader does with a layer: skip it, make it only for the masks of higher layers, or show it. */
    static final int USE_NONE = 0, USE_MASK = 1, USE_SHOW = 2;

    // The float offsets in a layer.
    static final int KIND = 0, BLEND = 1, USE = 2, MASK_INDEX = 3, MASK_DARK = 4, OPACITY = 5, AMOUNT = 6, HEIGHT = 7;
    static final int SCALE = 8, SEED = 9, ALPHA_1 = 10, ALPHA_2 = 11, COLOR_1 = 12, TERM_SLOT = 15;
    static final int COLOR_2 = 16, LAB_1 = 20, LAB_2 = 24;
    /** The values of a galaxy in the free last floats of the color vec4 values: the tilt from 0 to 1 of 90 degrees, the dust and the jets. */
    static final int TILT = 19, DUST = 23, JETS = 27;
    /** The values of the meteors and the aurora in the same free floats as the galaxy values, because each kind reads only its own values. */
    static final int SPEED = 19, LENGTH = 23, SHOWER = 27;
    /** The three rows of the matrix from sky directions to the layer frame, each a vec4. */
    static final int LOCAL = 28;
    /** The linear jet color, one channel in the free last float of each row of the matrix. */
    static final int COLOR_3 = LOCAL + 3;

    private final float[] block = new float[FLOATS];
    private final Path[] paths;
    private final int terms;
    private final boolean moves;

    private SkyProgram(SkyDesign design, float[] ring) {
        List<SkyLayer> layers = design.layers();
        // The header: the layer count, the extent (1 for the full sphere), the term count and the time.
        block[0] = layers.size();
        block[1] = design.extent() == SkyDesign.Extent.SPHERE ? 1 : 0;
        block[TIME] = STILL;
        boolean anyMoves = false;
        paths = new Path[layers.size()];
        boolean[] maskSource = new boolean[layers.size()];
        for (int i = 0; i < layers.size(); i++) {
            SkyMask m = layers.get(i).mask();
            int source = m.isNone() ? -1 : design.indexOf(m.layer());
            if (source >= 0 && source < i) maskSource[source] = true;
        }
        int slots = 0;
        for (int i = 0; i < layers.size(); i++) {
            SkyLayer l = layers.get(i);
            paths[i] = path(l.kind());
            if (paths[i] == Path.TERM && slots == MAX_TERMS) paths[i] = Path.BAKED;
            int base = LAYERS + i * LAYER_VEC4S * 4;
            block[base + TERM_SLOT] = paths[i] == Path.TERM ? slots++ : -1;
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
            anyMoves |= movesWithTime(l.kind()) && block[base + USE] != USE_NONE;
            color(l.param(SkyLayer.Param.COLOR_1), base + ALPHA_1, base + COLOR_1, base + LAB_1);
            color(l.param(SkyLayer.Param.COLOR_2), base + ALPHA_2, base + COLOR_2, base + LAB_2);
            if (l.kind() == SkyLayer.Kind.GALAXY) {
                block[base + TILT] = l.param(SkyLayer.Param.TILT) / 90f;
                block[base + DUST] = l.param(SkyLayer.Param.DUST) / 100f;
                block[base + JETS] = l.param(SkyLayer.Param.JETS) / 100f;
                float[] jet = Oklab.linear(l.param(SkyLayer.Param.COLOR_3));
                for (int c = 0; c < 3; c++) block[base + COLOR_3 + c * 4] = jet[c];
            } else if (l.kind() == SkyLayer.Kind.METEORS) {
                block[base + SPEED] = l.param(SkyLayer.Param.SPEED) / 100f;
                block[base + LENGTH] = l.param(SkyLayer.Param.LENGTH) / 100f;
                block[base + SHOWER] = l.param(SkyLayer.Param.SHOWER) / 100f;
            } else if (l.kind() == SkyLayer.Kind.AURORA) {
                block[base + SPEED] = l.param(SkyLayer.Param.SPEED) / 100f;
            }
            double[] r = l.transform().rotation();
            for (int row = 0; row < 3; row++) {
                for (int c = 0; c < 3; c++) block[base + LOCAL + row * 4 + c] = (float) r[c * 3 + row];
            }
        }
        terms = slots;
        moves = anyMoves;
        block[2] = slots;
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

    /** The path of a kind: a filter that reads near pixels or other directions is a term image, and the other kinds are pixels. */
    public static Path path(SkyLayer.Kind kind) {
        switch (kind) {
            case GRADIENT:
            case GLOW:
            case NOISE:
            case FRACTAL:
            case GALAXY:
            case STARS:
            case METEORS:
            case AURORA:
            case POSTERIZE:
            case HUE_SHIFT:
            case VIGNETTE:
                return Path.PIXEL;
            case BLUR:
            case PIXELATE:
            case CRYSTALLIZE:
            case SWIRL:
            case BLOOM:
                return Path.TERM;
            default:
                throw new IllegalArgumentException("no path for " + kind);
        }
    }

    /** The block, which the caller must not change. */
    public float[] block() { return block; }

    /** The path of each layer from the bottom up. */
    public Path path(int layer) { return paths[layer]; }

    /** True when the shader draws all layers, some from their term images, so the sky can draw sharp with no baked image. */
    public boolean drawsSharp() {
        for (Path p : paths) if (p == Path.BAKED) return false;
        return true;
    }

    /** True when a layer that shows or masks moves with the time, so that the adapter must write the time into the header for each frame. */
    public boolean moves() { return moves; }

    /** The number of term images, from 0 to {@link #MAX_TERMS}. */
    public int terms() { return terms; }

    /** The term image of a layer, or -1 when it has none. */
    public int termSlot(int layer) {
        return (int) block[at(layer, TERM_SLOT)];
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

    /** True for a kind that changes with the time of the header. */
    private static boolean movesWithTime(SkyLayer.Kind kind) {
        switch (kind) {
            case METEORS:
            case AURORA:
                return true;
            default:
                return false;
        }
    }

    /** The kind code of the shader, where the filters are the codes from {@link #KIND_BLOOM} to {@link #KIND_VIGNETTE}. */
    static int kindCode(SkyLayer.Kind kind) {
        switch (kind) {
            case GRADIENT: return KIND_GRADIENT;
            case GLOW: return KIND_GLOW;
            case STARS: return KIND_STARS;
            case NOISE: return KIND_NOISE;
            case FRACTAL: return KIND_FRACTAL;
            case GALAXY: return KIND_GALAXY;
            case METEORS: return KIND_METEORS;
            case BLOOM: return KIND_BLOOM;
            case BLUR: return KIND_BLUR;
            case PIXELATE: return KIND_PIXELATE;
            case CRYSTALLIZE: return KIND_CRYSTALLIZE;
            case SWIRL: return KIND_SWIRL;
            case POSTERIZE: return KIND_POSTERIZE;
            case HUE_SHIFT: return KIND_HUE_SHIFT;
            case VIGNETTE: return KIND_VIGNETTE;
            case AURORA: return KIND_AURORA;
            default: throw new IllegalArgumentException("no kind code for " + kind);
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
