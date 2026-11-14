package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Transform;

import java.util.List;

/**
 * The numbers of a {@link SkyDesign} in one block of floats that the CPU bake and the sky shader both read, laid out as a
 * std140 uniform block of vec4 values: a header, {@link SkyDesign#MAX_LAYERS} layers of {@link #LAYER_VEC4S} each, and
 * the {@link SkyImage#RING} horizon colors.
 */
public final class SkyProgram {
    /** Where the GPU gets the pixels of a layer. */
    enum Path {
        /** The sky shader computes the layer for each screen pixel. */
        PIXEL,
        /** The CPU bake makes a soft term image of the layer, which the sky shader mixes in for each screen pixel. */
        TERM,
        /** Only the CPU bake makes the layer, because the design has no free term slot, so the sky draws from the baked image. */
        BAKED
    }

    /** The largest number of term images of one design. */
    public static final int MAX_TERMS = 4;
    /**
     * The largest number of term and field images of one design, where a field image holds the smooth noise of a cloud or
     * fractal layer at the half size of the bake, so that the sky shader reads it in place of most noise octaves.
     */
    public static final int MAX_SLOTS = 6;
    /** The vec4 values of each layer. */
    public static final int LAYER_VEC4S = 10;
    /** The float index of the layer count in the header. */
    static final int LAYER_COUNT = 0;
    /** The float index of the extent in the header: 1 for the full sphere, else 0. */
    static final int SPHERE = 1;
    /** The float index of the count of term and field images in the header. */
    static final int TERM_COUNT = 2;
    /** The float index of the time in seconds, the last value of the header, which each adapter writes for each frame. */
    public static final int TIME = 3;
    /** The float index of the three rows of the turn from sky directions to world directions, each a vec4 after the header. */
    public static final int WORLD = 4;
    /** The float index of the first layer, after the header and the turn. */
    public static final int LAYERS = WORLD + 3 * 4;
    /** The float index of the first horizon color. */
    public static final int RING = LAYERS + SkyDesign.MAX_LAYERS * LAYER_VEC4S * 4;
    /** The number of floats in the block. */
    public static final int FLOATS = RING + SkyImage.RING * 4;
    /** The time of a still sky, in which the layers that move draw nothing, as in the CPU bake. */
    public static final float STILL = -1f;

    /** The parts of the block as the plain uniforms of sky_layers.glsl, for the GL programs that set each part on its own. */
    public enum Uniform {
        HEADER("SkyHeader", 0, SkyProgram.WORLD),
        WORLD("SkyWorld", SkyProgram.WORLD, SkyProgram.LAYERS),
        LAYERS("SkyLayers", SkyProgram.LAYERS, SkyProgram.RING),
        RING("SkyRing", SkyProgram.RING, SkyProgram.FLOATS);

        private final String glslName;
        private final int from;
        private final int to;

        Uniform(String glslName, int from, int to) {
            this.glslName = glslName;
            this.from = from;
            this.to = to;
        }

        public String glslName() {
            return glslName;
        }

        /** The float index of the first value of this part in the block. */
        public int from() {
            return from;
        }

        /** The number of floats of this part. */
        public int length() {
            return to - from;
        }
    }

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
    /** The kind code of the deep field, a generator that the shader calculates for each pixel. */
    static final int KIND_DEEP_FIELD = 17;
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
    /** The detail of a galaxy in the float of the height and its motion in the first float of the first OKLab color: a galaxy reads neither. */
    static final int DETAIL = HEIGHT, MOTION = LAB_1;
    /** The values of the meteors and the aurora in the same free floats as the galaxy values, because each kind reads only its own values. */
    static final int SPEED = 19, LENGTH = 23, SHOWER = 27;
    /** The twinkle of the stars and the pulse of the noise and fractal clouds, in the same free float as the speed. */
    static final int TWINKLE = 19, PULSE = 19;
    /** The three rows of the matrix from sky directions to the layer frame, each a vec4. */
    static final int LOCAL = 28;
    /** The linear jet color, one channel in the free last float of each row of the matrix. */
    static final int COLOR_3 = LOCAL + 3;
    /** The trail arc of the stars in radians (0 for no trails) and the trail light, in the same free floats as the galaxy dust and jets. */
    static final int TRAIL = 23, TRAIL_LIGHT = 27;
    /** The trail axis of the stars in the layer frame ({@link SkyTurn#trailAxis}), in the same free floats as the jet color. */
    static final int TRAIL_AXIS = COLOR_3;
    /** The aurora clock of the frame ({@link Aurora#clock}), in the same free float as the length of the meteors. */
    static final int CLOCK = LENGTH;

    private final float[] block = new float[FLOATS];
    /** The block of the last {@link #at}, which the render thread fills again for each frame. */
    private final float[] frame = new float[FLOATS];
    private final Path[] paths;
    private final int terms;
    private final boolean moves;
    private String steps;

    private SkyProgram(SkyDesign design, float[] ring) {
        List<SkyLayer> layers = design.layers();
        writeHeader(design);
        boolean[] maskSource = maskSources(design);
        paths = new Path[layers.size()];
        int slots = 0;
        boolean anyMoves = false;
        for (int i = 0; i < layers.size(); i++) {
            slots = writeLayer(i, design, maskSource[i], slots);
            anyMoves |= movesWithTime(layers.get(i)) && block[index(i, USE)] != USE_NONE;
        }
        terms = assignFieldSlots(layers, slots);
        moves = anyMoves;
        block[TERM_COUNT] = terms;
        writeRing(ring);
    }

    /**
     * The program of a design.
     *
     * @param ring the linear {r, g, b} of each horizon sector, from the bake
     */
    static SkyProgram of(SkyDesign design, float[] ring) {
        return new SkyProgram(design, ring);
    }

    /** Writes the header of a still sky and the identity turn. */
    private void writeHeader(SkyDesign design) {
        block[LAYER_COUNT] = design.layers().size();
        block[SPHERE] = design.drawnExtent() == SkyDesign.Extent.SPHERE ? 1 : 0;
        block[TIME] = STILL;
        for (int row = 0; row < 3; row++) block[WORLD + row * 5] = 1;
    }

    /** True for each layer that a higher layer uses as its mask. */
    private static boolean[] maskSources(SkyDesign design) {
        List<SkyLayer> layers = design.layers();
        boolean[] maskSource = new boolean[layers.size()];
        for (int i = 0; i < layers.size(); i++) {
            int source = maskIndex(design, i);
            if (source >= 0 && source < i) maskSource[source] = true;
        }
        return maskSource;
    }

    /** The index of the mask layer of a layer, or -1 when it has no mask. */
    private static int maskIndex(SkyDesign design, int layer) {
        SkyMask m = design.layers().get(layer).mask();
        return m.isNone() ? -1 : design.indexOf(m.layer());
    }

    /**
     * Writes the path and the values of one layer.
     *
     * @param slots the term slots that the lower layers use
     * @return the term slots that this layer and the lower layers use
     */
    private int writeLayer(int i, SkyDesign design, boolean maskSource, int slots) {
        SkyLayer l = design.layers().get(i);
        ShaderKind kind = ShaderKind.of(l.kind());
        paths[i] = kind.path;
        if (paths[i] == Path.TERM && slots == MAX_TERMS) paths[i] = Path.BAKED;
        int base = index(i, 0);
        block[base + TERM_SLOT] = paths[i] == Path.TERM ? slots++ : -1;
        block[base + KIND] = kind.code;
        block[base + BLEND] = blendCode(l.blend());
        block[base + USE] = l.output() == SkyLayer.Output.SHOW ? USE_SHOW : maskSource ? USE_MASK : USE_NONE;
        int source = maskIndex(design, i);
        block[base + MASK_INDEX] = source < i ? source : -1;
        block[base + MASK_DARK] = l.mask().mode() == SkyMask.Mode.DARK ? 1 : 0;
        block[base + OPACITY] = l.opacity() / 100f;
        block[base + AMOUNT] = l.param(SkyLayer.Param.AMOUNT) / 100f;
        block[base + HEIGHT] = l.param(SkyLayer.Param.HEIGHT) / 100f;
        block[base + SCALE] = l.transform().scale() / 100f;
        block[base + SEED] = l.param(SkyLayer.Param.SEED);
        color(l.param(SkyLayer.Param.COLOR_1), base + ALPHA_1, base + COLOR_1, base + LAB_1);
        color(l.param(SkyLayer.Param.COLOR_2), base + ALPHA_2, base + COLOR_2, base + LAB_2);
        writeKindValues(l, design, base);
        double[] r = l.transform().rotation();
        for (int row = 0; row < 3; row++) {
            for (int c = 0; c < 3; c++) block[base + LOCAL + row * 4 + c] = (float) r[c * 3 + row];
        }
        return slots;
    }

    /** Writes the values that only one kind reads, after the common values, because the galaxy detail and motion use their floats. */
    private void writeKindValues(SkyLayer l, SkyDesign design, int base) {
        if (l.kind() == SkyLayer.Kind.GALAXY) {
            block[base + TILT] = l.param(SkyLayer.Param.TILT) / 90f;
            block[base + DUST] = l.param(SkyLayer.Param.DUST) / 100f;
            block[base + JETS] = l.param(SkyLayer.Param.JETS) / 100f;
            block[base + DETAIL] = l.param(SkyLayer.Param.DETAIL) / 100f;
            block[base + MOTION] = l.param(SkyLayer.Param.MOTION) / 100f;
            float[] jet = Oklab.linear(l.param(SkyLayer.Param.COLOR_3));
            for (int c = 0; c < 3; c++) block[base + COLOR_3 + c * 4] = jet[c];
        } else if (l.kind() == SkyLayer.Kind.METEORS) {
            block[base + SPEED] = l.param(SkyLayer.Param.SPEED) / 100f;
            block[base + LENGTH] = l.param(SkyLayer.Param.LENGTH) / 100f;
            block[base + SHOWER] = l.param(SkyLayer.Param.SHOWER) / 100f;
        } else if (l.kind() == SkyLayer.Kind.AURORA) {
            block[base + SPEED] = l.param(SkyLayer.Param.SPEED) / 100f;
        } else if (l.kind() == SkyLayer.Kind.STARS) {
            block[base + TWINKLE] = l.param(SkyLayer.Param.TWINKLE) / 100f;
            if (design.turning().showsTrails()) trails(design.turning(), l.transform(), base);
        } else if (l.kind() == SkyLayer.Kind.NOISE || l.kind() == SkyLayer.Kind.FRACTAL) {
            block[base + PULSE] = l.param(SkyLayer.Param.PULSE) / 100f;
        }
    }

    /**
     * Gives the cloud and fractal layers that show or mask the free slots up to {@link #MAX_SLOTS} for their field images, in
     * their order, only when the shader can draw the sky; call it after all paths are set, because it reads {@link #drawsSharp}.
     *
     * @param slots the term slots of the filters
     * @return the term and field slots
     */
    private int assignFieldSlots(List<SkyLayer> layers, int slots) {
        for (int i = 0; i < layers.size() && drawsSharp(); i++) {
            SkyLayer.Kind kind = layers.get(i).kind();
            int base = index(i, 0);
            if ((kind == SkyLayer.Kind.NOISE || kind == SkyLayer.Kind.FRACTAL) && block[base + USE] != USE_NONE && slots < MAX_SLOTS) {
                block[base + TERM_SLOT] = slots++;
            }
        }
        return slots;
    }

    private void writeRing(float[] ring) {
        for (int s = 0; s < SkyImage.RING; s++) System.arraycopy(ring, s * 3, block, RING + s * 4, 3);
    }

    /** The block of a still sky that does not turn, which the caller must not change. */
    public float[] block() { return block; }

    /**
     * The block of one frame, with the time of the layers that move and the aurora clocks at a time of the sky clock, and
     * the turn of the sky, so that the layers in the air stay in the world; each call fills the same array again.
     *
     * @param seconds the time of the sky clock with no wrap ({@link SkyClock#longSeconds})
     */
    public float[] at(double seconds, SkyTurn turn) {
        System.arraycopy(block, 0, frame, 0, FLOATS);
        turn.rows(frame, WORLD);
        if (!moves) return frame;
        frame[TIME] = (float) SkyClock.wrap(seconds);
        for (int i = 0; i < paths.length; i++) {
            if ((int) block[index(i, KIND)] == KIND_AURORA) frame[index(i, CLOCK)] = Aurora.clock(seconds, block[index(i, SPEED)]);
        }
        return frame;
    }

    Path path(int layer) { return paths[layer]; }

    /** True when the shader draws all layers, some from their term images, so the sky can draw sharp with no baked image. */
    public boolean drawsSharp() {
        for (Path p : paths) if (p == Path.BAKED) return false;
        return true;
    }

    /** True when a layer that shows or masks moves with the time, so that the adapter must write the time into the header for each frame. */
    public boolean moves() { return moves; }

    /** The {@link SkyLayerSteps#layerSteps} of this program, which the render thread reads in each frame to find its shader. */
    public String shaderSteps() {
        if (steps == null) steps = SkyLayerSteps.layerSteps(this);
        return steps;
    }

    /** The number of term and field images, from 0 to {@link #MAX_SLOTS}. */
    public int terms() { return terms; }

    /** The term image of a filter or the field image of a cloud or fractal layer, or -1 when it has none. */
    int termSlot(int layer) {
        return (int) block[index(layer, TERM_SLOT)];
    }

    /** The float index of a value of a layer. */
    static int index(int layer, int offset) {
        return LAYERS + layer * LAYER_VEC4S * 4 + offset;
    }

    /**
     * Writes the trails of a stars layer: the arc, the light and the trail axis turned into the layer frame, where the arc
     * of a layer smaller than 100 is at most the largest Trails times its size, because a pixel reads the cells along the
     * whole arc and a smaller layer has more cells.
     */
    private void trails(SkyTurning turning, Transform transform, int base) {
        double arc = Math.toRadians(turning.trails()), longest = Math.toRadians(SkyTurning.MAX_TRAILS) * transform.scale() / 100.0;
        block[base + TRAIL] = (float) Math.min(arc, longest);
        double[] rotation = transform.rotation();
        block[base + TRAIL_LIGHT] = turning.trailLight() / 100f;
        double[] axis = SkyTurn.trailAxis(turning);
        for (int row = 0; row < 3; row++) {
            double v = 0;
            for (int c = 0; c < 3; c++) v += rotation[c * 3 + row] * axis[c];
            block[base + TRAIL_AXIS + row * 4] = (float) v;
        }
    }

    private void color(int argb, int alpha, int linear, int lab) {
        block[alpha] = (argb >>> 24) / 255f;
        float[] rgb = Oklab.linear(argb);
        System.arraycopy(rgb, 0, block, linear, 3);
        float[] out = new float[3];
        Oklab.fromLinear(rgb[0], rgb[1], rgb[2], out);
        System.arraycopy(out, 0, block, lab, 3);
    }

    /** True for a layer that changes with the time of the header. */
    private static boolean movesWithTime(SkyLayer layer) {
        switch (layer.kind()) {
            case METEORS:
            case AURORA:
                return true;
            case STARS:
                return layer.param(SkyLayer.Param.TWINKLE) > 0;
            case NOISE:
            case FRACTAL:
                return layer.param(SkyLayer.Param.PULSE) > 0;
            case GALAXY:
                return layer.param(SkyLayer.Param.MOTION) > 0;
            default:
                return false;
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
