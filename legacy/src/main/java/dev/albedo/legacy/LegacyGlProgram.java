package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.shader.ShaderSource;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyLayerSteps;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;

/** A GL program from one classic shader file that compiles on its first use, the LWJGL 2 twin of the modern GlProgram. */
class LegacyGlProgram {
    /** The length of the buffer that takes the info log of the GL. */
    private static final int INFO_LOG_LENGTH = 32768;

    private final String name;
    private final String steps;
    private final ShaderSource.Dialect dialect;
    private final String what;
    private final String fallback;
    private ShaderState state = ShaderState.NOT_STARTED;
    private int id;

    /** A program of a shader file with the words of the log lines, which never compiles with a null dialect. */
    LegacyGlProgram(String name, String steps, ShaderSource.Dialect dialect, String what, String fallback) {
        this.name = name;
        this.steps = steps;
        this.dialect = dialect;
        this.what = what;
        this.fallback = fallback;
    }

    /** Compiles the program on its first use, and returns true when it compiled. */
    final boolean ready() {
        if (state == ShaderState.NOT_STARTED) compile();
        return state.usable();
    }

    final int id() {
        return id;
    }

    final int uniform(String uniform) {
        return GL20.glGetUniformLocation(id, uniform);
    }

    /** Takes the places of the uniforms after the program compiled. */
    void linked() {}

    final void close() {
        if (state.usable()) GL20.glDeleteProgram(id);
        state = state.to(ShaderState.CLOSED);
    }

    private void compile() {
        // A throw that no catch takes leaves RUNNING, so the program never compiles again.
        state = state.to(ShaderState.RUNNING);
        if (dialect == null) {
            state = state.to(ShaderState.FAILED);
            return;
        }
        int program = GL20.glCreateProgram();
        int vertex = 0;
        int fragment = 0;
        try {
            ShaderSource.Includes includes = SkyLayerSteps.includes(steps);
            vertex = shader(GL20.GL_VERTEX_SHADER, ShaderSource.classic(name, true, dialect, includes));
            fragment = shader(GL20.GL_FRAGMENT_SHADER, ShaderSource.classic(name, false, dialect, includes));
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, INFO_LOG_LENGTH));
            }
        } catch (IOException | IllegalStateException e) {
            GL20.glDeleteProgram(program);
            state = state.to(ShaderState.FAILED);
            Albedo.get().platform().warn("Cannot compile the " + what + " " + name + ". " + fallback, e);
            return;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
        id = program;
        state = state.to(ShaderState.READY);
        linked();
        Albedo.get().platform().info("Compiled the " + what + " " + name + " as " + dialect);
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, INFO_LOG_LENGTH);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }
}
