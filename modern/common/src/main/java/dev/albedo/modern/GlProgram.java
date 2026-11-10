package dev.albedo.modern;

//? if <1.21.11 {
/*import dev.albedo.core.Albedo;
import dev.albedo.core.sky.SkyShaderSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;

// A GL program of the mod from two classic shader files, which compiles on its first use, because a shader of the game
// must load its files as resources.
class GlProgram {
    private static final SkyShaderSource.Dialect DIALECT = SkyShaderSource.Dialect.UNIFORMS_150;

    private enum Compile { NOT_STARTED, READY, FAILED }

    private final String vertex;
    private final String fragment;
    private final String steps;
    private final String what;
    private final String fallback;
    private Compile state = Compile.NOT_STARTED;
    private int id;

    // A program from the vertex shader of one file and the fragment shader of an other, where what and fallback are the words of the log lines.
    GlProgram(String vertex, String fragment, String steps, String what, String fallback) {
        this.vertex = vertex;
        this.fragment = fragment;
        this.steps = steps;
        this.what = what;
        this.fallback = fallback;
    }

    // Compiles the program on its first use, and returns true when it compiled.
    final boolean ready() {
        if (state == Compile.NOT_STARTED) compile();
        return state == Compile.READY;
    }

    final int id() {
        return id;
    }

    // The place of a uniform of the compiled program.
    final int uniform(String name) {
        return GL20.glGetUniformLocation(id, name);
    }

    // Takes the places of the uniforms after the program compiled.
    void linked() {}

    // Deletes the GL program.
    final void close() {
        if (state == Compile.READY) GL20.glDeleteProgram(id);
        state = Compile.FAILED;
    }

    private void compile() {
        int program = GL20.glCreateProgram();
        int vertexShader = 0;
        int fragmentShader = 0;
        try {
            vertexShader = shader(GL20.GL_VERTEX_SHADER, SkyShaderSource.classic(vertex, true, DIALECT, steps));
            fragmentShader = shader(GL20.GL_FRAGMENT_SHADER, SkyShaderSource.classic(fragment, false, DIALECT, steps));
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            // A GlMesh puts the position in attribute 0 and the texture coordinates in attribute 1.
            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glBindAttribLocation(program, 1, "UV0");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            }
        } catch (IOException | IllegalStateException e) {
            GL20.glDeleteProgram(program);
            state = Compile.FAILED;
            Albedo.get().platform().warn("Cannot compile the " + what + " " + fragment + ". " + fallback, e);
            return;
        } finally {
            if (vertexShader != 0) GL20.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GL20.glDeleteShader(fragmentShader);
        }
        id = program;
        state = Compile.READY;
        linked();
        Albedo.get().platform().info("Compiled the " + what + " " + fragment);
    }

    // A compiled shader of a type, or an exception with the log of the GL.
    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }
}
*///?}
