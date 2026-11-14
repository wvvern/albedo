package dev.albedo.core.sky;

import dev.albedo.core.Albedo;
import dev.albedo.core.shader.ShaderSource;

/** The sky shader files, and the layer steps of each sky that go on top of the sky include. */
public final class SkyLayerSteps {
    /** The vertex and fragment shaders of the sky sphere for the classic GL programs. */
    public static final String SHARP = "core/sky_sharp_classic";
    /** The fragment shader that draws black on the sky sphere of {@link #SHARP} for the classic GL programs. */
    public static final String BLACK = "core/sky_black_classic";
    /** The vertex and fragment shaders that draw the baked sky image on the sky sphere, only for {@link ShaderSource.Dialect#UNIFORMS_150}. */
    public static final String BAKED = "core/sky_baked_classic";
    /** The vertex and fragment shaders that draw the sky into an equirectangular image for the classic GL programs. */
    public static final String EQUIRECT = "core/sky_equirect_classic";

    /** The include with the sky layers, which gets the layer steps of the sky on top. */
    public static final String LAYERS_INCLUDE = Albedo.MOD_ID + ":sky_layers.glsl";
    /** The ALBEDO_FLAG bits of sky_layers.glsl. */
    static final int FLAG_TWINKLE = 1, FLAG_TRAILS = 2, FLAG_SPIKES = 4, FLAG_PULSE = 8, FLAG_DUST = 16, FLAG_JETS = 32;
    static final int FLAG_DETAIL = 64, FLAG_MOTION = 128, FLAG_CLOUD = 256;

    private SkyLayerSteps() {}

    /**
     * The includes of the mod, with these {@link #layerSteps} on top of {@link #LAYERS_INCLUDE}, or an exception for the id
     * of an other namespace.
     */
    public static ShaderSource.Includes includes(String steps) {
        return id -> {
            String text = ShaderSource.readInclude(id);
            return id.equals(LAYERS_INCLUDE) ? steps + text : text;
        };
    }

    /**
     * The line that defines {@code ALBEDO_LAYER_STEPS} of the sky shader for a program, with a direct call of the function
     * of the kind of each layer that shows or masks and its parts, blend and mask as constants, so that the shader has no
     * code for other kinds and the GPU compiler inlines each step, and a shader compiles again only when this text changes.
     */
    public static String layerSteps(SkyProgram program) {
        float[] b = program.block();
        int count = (int) b[SkyProgram.LAYER_COUNT];
        boolean[] source = new boolean[count];
        for (int i = 0; i < count; i++) {
            int mask = (int) b[SkyProgram.index(i, SkyProgram.MASK_INDEX)];
            if (mask >= 0) source[mask] = true;
        }
        StringBuilder out = new StringBuilder("#define ALBEDO_LAYER_STEPS");
        for (int i = 0; i < count; i++) {
            int use = (int) b[SkyProgram.index(i, SkyProgram.USE)];
            if (use == SkyProgram.USE_NONE) continue;
            out.append(" s = ").append(call(b, i)).append(';');
            if (source[i]) out.append(" albedoMaskSource(").append(i).append(", s);");
            if (use != SkyProgram.USE_SHOW) continue;
            out.append(" img = albedoShow(img, s, ").append(i)
                    .append(", ").append((int) b[SkyProgram.index(i, SkyProgram.BLEND)])
                    .append(", ").append((int) b[SkyProgram.index(i, SkyProgram.MASK_INDEX)])
                    .append(", ").append(b[SkyProgram.index(i, SkyProgram.MASK_DARK)] > 0.5f)
                    .append(");");
        }
        return out.append('\n').toString();
    }

    /** True when a shader with these {@link #layerSteps} reads the term texture, which the compiler removes from a shader that does not. */
    public static boolean readsTerms(String steps) {
        return steps.contains("albedoField(") || steps.contains("albedoBloom(") || steps.contains("albedoTermFilter(");
    }

    /** The GLSL call that gives the color and alpha of a layer, over the sky {@code img} below it for a filter. */
    private static String call(float[] b, int layer) {
        String frame = "albedoFrame(" + layer + ", dir)";
        int flags = flags(b, layer);
        int kind = (int) b[SkyProgram.index(layer, SkyProgram.KIND)];
        // A cloud or fractal layer with a field image reads its slow noise from there.
        int slot = (int) b[SkyProgram.index(layer, SkyProgram.TERM_SLOT)];
        String field = slot >= 0 ? "albedoField(" + slot + ", dir)" : null;
        switch (kind) {
            case SkyProgram.KIND_GRADIENT: return "albedoGradient(" + layer + ", " + frame + ")";
            case SkyProgram.KIND_GLOW: return "albedoGlow(" + layer + ", " + frame + ")";
            case SkyProgram.KIND_STARS: return "albedoStars(" + layer + ", " + frame + ", footprint, " + flags + ")";
            case SkyProgram.KIND_NOISE:
                if (field == null) field = "albedoCloudsField(" + layer + ", " + frame + ", " + flags + ")";
                return "albedoClouds(" + layer + ", " + frame + ", " + field + ", " + flags + ")";
            case SkyProgram.KIND_FRACTAL:
                if (field == null) field = "albedoFractalField(" + layer + ", " + frame + ", " + flags + ")";
                return "albedoFractal(" + layer + ", " + frame + ", " + field + ", " + flags + ")";
            case SkyProgram.KIND_GALAXY: return "albedoGalaxy(" + layer + ", " + frame + ", footprint, " + flags + ")";
            case SkyProgram.KIND_DEEP_FIELD: return "albedoDeepField(" + layer + ", " + frame + ", footprint, " + flags + ")";
            // The layers in the air take the world direction, so that they do not turn with the sky.
            case SkyProgram.KIND_METEORS: return "albedoMeteors(" + layer + ", world, footprint)";
            case SkyProgram.KIND_AURORA: return "albedoAurora(" + layer + ", albedoFrame(" + layer + ", world), footprint)";
            case SkyProgram.KIND_BLOOM: return "albedoBloom(" + layer + ", img, dir)";
            case SkyProgram.KIND_BLUR:
            case SkyProgram.KIND_PIXELATE:
            case SkyProgram.KIND_CRYSTALLIZE:
            case SkyProgram.KIND_SWIRL:
                return "albedoTermFilter(" + layer + ", dir)";
            case SkyProgram.KIND_POSTERIZE: return "vec4(albedoPosterize(" + layer + ", img), 1.0)";
            case SkyProgram.KIND_HUE_SHIFT: return "vec4(albedoHueShift(" + layer + ", img), 1.0)";
            case SkyProgram.KIND_VIGNETTE: return "vec4(albedoVignette(" + layer + ", img, " + frame + "), 1.0)";
            default: throw new IllegalArgumentException("no shader function for the kind code " + kind);
        }
    }

    /** The ALBEDO_FLAG bits of the parts of a layer that its values turn on. */
    private static int flags(float[] b, int layer) {
        int kind = (int) b[SkyProgram.index(layer, SkyProgram.KIND)];
        int flags = 0;
        if (kind == SkyProgram.KIND_STARS) {
            if (b[SkyProgram.index(layer, SkyProgram.TWINKLE)] > 0) flags |= FLAG_TWINKLE;
            if (b[SkyProgram.index(layer, SkyProgram.TRAIL)] > 0) flags |= FLAG_TRAILS;
            if (b[SkyProgram.index(layer, SkyProgram.HEIGHT)] > 0) flags |= FLAG_SPIKES;
        } else if (kind == SkyProgram.KIND_NOISE || kind == SkyProgram.KIND_FRACTAL) {
            if (b[SkyProgram.index(layer, SkyProgram.PULSE)] > 0) flags |= FLAG_PULSE;
        } else if (kind == SkyProgram.KIND_GALAXY) {
            if (b[SkyProgram.index(layer, SkyProgram.DUST)] > 0) flags |= FLAG_DUST;
            if (b[SkyProgram.index(layer, SkyProgram.JETS)] > 0) flags |= FLAG_JETS;
            if (b[SkyProgram.index(layer, SkyProgram.DETAIL)] > 0) flags |= FLAG_DETAIL;
            if (b[SkyProgram.index(layer, SkyProgram.MOTION)] > 0) flags |= FLAG_MOTION;
        } else if (kind == SkyProgram.KIND_DEEP_FIELD) {
            if (b[SkyProgram.index(layer, SkyProgram.HEIGHT)] > 0) flags |= FLAG_CLOUD;
        }
        return flags;
    }
}
