package dev.albedo.core.sky;

import dev.albedo.core.Albedo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of the sky shaders from the jar of the mod with their includes in them, which the adapters give to the GPU
 * themselves, because a Fabric game without Fabric API does not load the assets of a mod.
 */
public final class SkyShaderSource {
    /** Gives the text of one include from its id, for example {@code minecraft:projection.glsl}. */
    public interface Includes {
        /** The text of the include, or an exception when there is no include with this id. */
        String read(String id) throws IOException;
    }

    /** The GLSL version and the defines of the classic sky shaders for one kind of GL context. */
    public enum Dialect {
        /** 1.21.11: GLSL 3.30 core, with the transforms in the uniform blocks of the game and the sky in its own block. */
        BLOCKS_330("#version 330\n#define ALBEDO_TRANSFORM_BLOCKS\n"),
        /** 1.21.1: GLSL 1.50 core, with plain uniforms for the transforms and the sky. */
        UNIFORMS_150("#version 150\n#define ALBEDO_PLAIN_UNIFORMS\n"),
        /** 1.8.9 with OpenGL 3.0: GLSL 1.30 with the fixed function matrices and plain uniforms for the sky. */
        FIXED_130("#version 130\n#define ALBEDO_PLAIN_UNIFORMS\n#define ALBEDO_FIXED_FUNCTION\n"),
        /** 1.8.9 with only EXT_gpu_shader4: GLSL 1.20 with the unsigned integers of the extension. */
        FIXED_120_EXT("#version 120\n#extension GL_EXT_gpu_shader4 : require\n#define uint unsigned int\n"
                + "#define ALBEDO_PLAIN_UNIFORMS\n#define ALBEDO_FIXED_FUNCTION\n");

        private final String header;

        Dialect(String header) {
            this.header = header;
        }

        /** The lines that go before the text of each shader file. */
        public String header() { return header; }
    }

    /** The vertex and fragment shaders of the sky sphere for the classic GL programs. */
    public static final String SHARP = "core/sky_sharp_classic";
    /** The vertex and fragment shaders that draw the sky into an equirectangular image for the classic GL programs. */
    public static final String EQUIRECT = "core/sky_equirect_classic";

    /** The include with the sky layers, which gets the layer steps of the sky on top. */
    public static final String LAYERS_INCLUDE = Albedo.MOD_ID + ":sky_layers.glsl";
    /** The ALBEDO_FLAG bits of sky_layers.glsl. */
    static final int FLAG_TWINKLE = 1, FLAG_TRAILS = 2, FLAG_SPIKES = 4, FLAG_PULSE = 8, FLAG_DUST = 16, FLAG_JETS = 32;

    private static final String FOLDER = "/assets/albedo/shaders/";
    /** An include line, with the id between angle brackets or quotes. */
    private static final Pattern INCLUDE = Pattern.compile("\\s*#\\s*include\\s*[<\"]([^>\"]+)[>\"].*");
    /** The include depth that stops a loop of includes. */
    private static final int MAX_INCLUDE_DEPTH = 10;

    private SkyShaderSource() {}

    /** A text file from the shader folder of the mod, for example {@code include/sky_layers.glsl}. */
    public static String read(String path) throws IOException {
        try (InputStream in = SkyShaderSource.class.getResourceAsStream(FOLDER + path)) {
            if (in == null) throw new IOException("no file " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * One complete classic shader: the header of the dialect, then the file with the text of the sky include in place of
     * its include line.
     *
     * @param name {@link #SHARP} or {@link #EQUIRECT}
     * @param vertex true for the vertex shader, false for the fragment shader
     * @param steps the {@link #layerSteps} of the sky that the shader draws
     */
    public static String classic(String name, boolean vertex, Dialect dialect, String steps) throws IOException {
        return dialect.header() + inline(read(name + (vertex ? ".vsh" : ".fsh")), id -> readInclude(id, steps));
    }

    /**
     * An include of the mod, for example {@code albedo:sky_layers.glsl}, or an exception for the id of an other namespace.
     *
     * @param steps the {@link #layerSteps} that go on top of the sky include
     */
    public static String readInclude(String id, String steps) throws IOException {
        String prefix = Albedo.MOD_ID + ":";
        if (!id.startsWith(prefix)) throw new IOException("no include " + id);
        String text = read("include/" + id.substring(prefix.length()));
        return id.equals(LAYERS_INCLUDE) ? steps + text : text;
    }

    /**
     * The line that defines {@code ALBEDO_LAYER_STEPS} of the sky shader for a program, with a direct call of the function
     * of the kind of each layer that shows or masks and its parts, blend and mask as constants, so that the shader has no
     * code for other kinds and the GPU compiler inlines each step, and a shader compiles again only when this text changes.
     */
    public static String layerSteps(SkyProgram program) {
        float[] b = program.block();
        int count = (int) b[0];
        boolean[] source = new boolean[count];
        for (int i = 0; i < count; i++) {
            int mask = (int) b[SkyProgram.at(i, SkyProgram.MASK_INDEX)];
            if (mask >= 0) source[mask] = true;
        }
        StringBuilder out = new StringBuilder("#define ALBEDO_LAYER_STEPS");
        for (int i = 0; i < count; i++) {
            int use = (int) b[SkyProgram.at(i, SkyProgram.USE)];
            if (use == SkyProgram.USE_NONE) continue;
            out.append(" s = ").append(call(b, i)).append(';');
            if (source[i]) out.append(" albedoMaskSource(").append(i).append(", s);");
            if (use != SkyProgram.USE_SHOW) continue;
            out.append(" img = albedoShow(img, s, ").append(i)
                    .append(", ").append((int) b[SkyProgram.at(i, SkyProgram.BLEND)])
                    .append(", ").append((int) b[SkyProgram.at(i, SkyProgram.MASK_INDEX)])
                    .append(", ").append(b[SkyProgram.at(i, SkyProgram.MASK_DARK)] > 0.5f)
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
        int kind = (int) b[SkyProgram.at(layer, SkyProgram.KIND)];
        // A cloud or fractal layer with a field image reads its slow noise from there.
        int slot = (int) b[SkyProgram.at(layer, SkyProgram.TERM_SLOT)];
        String field = slot >= 0 ? "albedoField(" + slot + ", dir)" : null;
        switch (kind) {
            case SkyProgram.KIND_GRADIENT: return "albedoGradient(" + layer + ", " + frame + ")";
            case SkyProgram.KIND_GLOW: return "albedoGlow(" + layer + ", " + frame + ")";
            case SkyProgram.KIND_STARS: return "albedoStars(" + layer + ", " + frame + ", footprint, " + flags + ")";
            case SkyProgram.KIND_NOISE:
                if (field == null) field = "albedoCloudsField(" + layer + ", " + frame + ", " + flags + ")";
                return "albedoClouds(" + layer + ", " + field + ", " + flags + ")";
            case SkyProgram.KIND_FRACTAL:
                if (field == null) field = "albedoFractalField(" + layer + ", " + frame + ", " + flags + ")";
                return "albedoFractal(" + layer + ", " + frame + ", " + field + ", " + flags + ")";
            case SkyProgram.KIND_GALAXY: return "albedoGalaxy(" + layer + ", " + frame + ", footprint, " + flags + ")";
            case SkyProgram.KIND_METEORS: return "albedoMeteors(" + layer + ", " + frame + ", footprint)";
            case SkyProgram.KIND_AURORA: return "albedoAurora(" + layer + ", " + frame + ", footprint)";
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
        int kind = (int) b[SkyProgram.at(layer, SkyProgram.KIND)];
        int flags = 0;
        if (kind == SkyProgram.KIND_STARS) {
            if (b[SkyProgram.at(layer, SkyProgram.TWINKLE)] > 0) flags |= FLAG_TWINKLE;
            if (b[SkyProgram.at(layer, SkyProgram.TRAIL)] > 0) flags |= FLAG_TRAILS;
            if (b[SkyProgram.at(layer, SkyProgram.HEIGHT)] > 0) flags |= FLAG_SPIKES;
        } else if (kind == SkyProgram.KIND_NOISE || kind == SkyProgram.KIND_FRACTAL) {
            if (b[SkyProgram.at(layer, SkyProgram.PULSE)] > 0) flags |= FLAG_PULSE;
        } else if (kind == SkyProgram.KIND_GALAXY) {
            if (b[SkyProgram.at(layer, SkyProgram.DUST)] > 0) flags |= FLAG_DUST;
            if (b[SkyProgram.at(layer, SkyProgram.JETS)] > 0) flags |= FLAG_JETS;
        }
        return flags;
    }

    /**
     * The text of a shader with the text of each include in place of its include line, as the include step of the compiler
     * does it, so that the compiler never calls back into Java for an include.
     */
    public static String inline(String text, Includes includes) throws IOException {
        StringBuilder out = new StringBuilder(text.length());
        inline(text, includes, 0, out);
        return out.toString();
    }

    private static void inline(String text, Includes includes, int depth, StringBuilder out) throws IOException {
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher include = INCLUDE.matcher(lines[i]);
            if (!include.matches()) {
                out.append(lines[i]).append('\n');
                continue;
            }
            String id = include.group(1);
            if (depth >= MAX_INCLUDE_DEPTH) throw new IOException("includes too deep at " + id);
            String body = includes.read(id);
            if (body == null) throw new IOException("no include " + id);
            inline(body, includes, depth + 1, out);
            // The lines after the include keep their numbers in the messages of the compiler.
            out.append("#line ").append(i + 2).append('\n');
        }
    }
}
