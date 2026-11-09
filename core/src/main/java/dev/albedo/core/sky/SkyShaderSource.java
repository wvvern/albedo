package dev.albedo.core.sky;

import dev.albedo.core.Albedo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

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

    private static final String FOLDER = "/assets/albedo/shaders/";
    private static final String INCLUDE = "#include <";

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
     */
    public static String classic(String name, boolean vertex, Dialect dialect) throws IOException {
        return dialect.header() + inline(read(name + (vertex ? ".vsh" : ".fsh")), SkyShaderSource::readInclude);
    }

    /** An include of the mod, for example {@code albedo:sky_layers.glsl}, or an exception for the id of an other namespace. */
    public static String readInclude(String id) throws IOException {
        String prefix = Albedo.MOD_ID + ":";
        if (!id.startsWith(prefix)) throw new IOException("no include " + id);
        return read("include/" + id.substring(prefix.length()));
    }

    /**
     * The text of a shader with the text of each include in place of its include line, and each include only one time, so
     * that the compiler of the shader never calls back into Java for an include.
     */
    public static String inline(String text, Includes includes) throws IOException {
        StringBuilder out = new StringBuilder(text.length());
        inline(text, includes, new HashSet<String>(), out);
        return out.toString();
    }

    private static void inline(String text, Includes includes, Set<String> done, StringBuilder out) throws IOException {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(INCLUDE) || !trimmed.endsWith(">")) {
                out.append(line).append('\n');
                continue;
            }
            String id = trimmed.substring(INCLUDE.length(), trimmed.length() - 1);
            if (done.add(id)) inline(includes.read(id), includes, done, out);
        }
    }
}
