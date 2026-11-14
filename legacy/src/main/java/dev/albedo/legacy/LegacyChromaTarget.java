package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.texture.ChromaTexels;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * The GL texture of the block atlas with its pristine copy and its texels, into which the Chroma pass draws, where
 * withState, sizeOf, baseLevel and copy are twins of FixedChromaPass and a fix lands in both.
 */
final class LegacyChromaTarget {
    /** The Chroma program, which takes its uniforms and makes the framebuffer of the pass when it compiled. */
    static final class Program extends LegacyGlProgram {
        private int turnsUniform;
        private int sizeUniform;
        private int framebuffer;

        Program() {
            super(SkyShaderSource.CHROMA, "", dialect(), "Chroma shader", "Chroma is off.");
        }

        /** The framebuffer of the pass, or 0 before the program compiled. */
        int framebuffer() {
            return framebuffer;
        }

        @Override
        void linked() {
            turnsUniform = uniform("ChromaTurns");
            sizeUniform = uniform("ChromaSize");
            int pristineUniform = uniform("AlbedoPristine");
            int texelsUniform = uniform("AlbedoTexels");
            withState(framebuffer, () -> {
                GL20.glUseProgram(id());
                GL20.glUniform1i(pristineUniform, 0);
                GL20.glUniform1i(texelsUniform, 1);
            });
            framebuffer = OpenGlHelper.glGenFramebuffers();
        }

        /** GLSL 1.30 with OpenGL 3.0, else GLSL 1.20, or null with a log line without OpenGL 2.0 or framebuffers. */
        private static SkyShaderSource.Dialect dialect() {
            ContextCapabilities caps = GLContext.getCapabilities();
            if (!caps.OpenGL20 || !OpenGlHelper.framebufferSupported) {
                Albedo.get().platform().warn("The GPU has no OpenGL 2.0 or no framebuffers, so Chroma is off", null);
                return null;
            }
            return SkyShaderSource.Dialect.chroma(caps.OpenGL30);
        }
    }

    private static final FloatBuffer TURNS = BufferUtils.createFloatBuffer(Chroma.TURN_FLOATS);

    /** The GL texture of the atlas. */
    final int live;
    /** The width, height and number of mip levels of the atlas. */
    final int[] size;
    private final Program program;
    private final int framebuffer;
    private final int pristine;
    private final int texels;

    /** Makes the pristine copy and the texels for an atlas of this size, with a compiled program. */
    LegacyChromaTarget(Program program, int live, int[] size) {
        this.program = program;
        this.framebuffer = program.framebuffer();
        this.live = live;
        this.size = size;
        this.pristine = texture();
        this.texels = texture();
    }

    /** Sends the texels of each mip level to the GPU. */
    void texels(int[][] levels) {
        withState(framebuffer, () -> {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texels);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            for (int level = 0; level < size[2]; level++) {
                ByteBuffer bytes = BufferUtils.createByteBuffer(levels[level].length * 4);
                ChromaTexels.rgba(levels[level], bytes);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width(level), height(level), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            }
        });
    }

    /** Copies each mip level of the atlas into the pristine copy, then draws the colors of these turns into the atlas. */
    void recolor(float[] turns) {
        withState(framebuffer, () -> {
            for (int level = 0; level < size[2]; level++) copy(live, pristine, level);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            GL20.glUseProgram(program.id());
            TURNS.clear();
            TURNS.put(turns).flip();
            GL20.glUniform4(program.turnsUniform, TURNS);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            for (int level = 0; level < size[2]; level++) {
                OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, live, level);
                GL11.glViewport(0, 0, width(level), height(level));
                GL20.glUniform2f(program.sizeUniform, width(level), height(level));
                baseLevel(GL13.GL_TEXTURE1, texels, level);
                baseLevel(GL13.GL_TEXTURE0, pristine, level);
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex2f(-1, -1);
                GL11.glVertex2f(3, -1);
                GL11.glVertex2f(-1, 3);
                GL11.glEnd();
            }
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            // A framebuffer cannot read a level below the base level, so the copies at the end of the frame need base level 0.
            baseLevel(GL13.GL_TEXTURE1, texels, 0);
            baseLevel(GL13.GL_TEXTURE0, pristine, 0);
        });
    }

    /** Copies each mip level of the pristine copy back into the atlas. */
    void restore() {
        withState(framebuffer, () -> {
            for (int level = 0; level < size[2]; level++) copy(pristine, live, level);
        });
    }

    /** The GPU memory of the pristine copy or of the texels. */
    long bytes() {
        long out = 0;
        for (int level = 0; level < size[2]; level++) out += ChromaTexels.levelBytes(width(level), height(level));
        return out;
    }

    void close() {
        GL11.glDeleteTextures(pristine);
        GL11.glDeleteTextures(texels);
    }

    /** Binds a texture to a unit with one level as its base level, which the shader reads as its level 0. */
    private static void baseLevel(int unit, int texture, int level) {
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, level);
    }

    /** Copies one mip level of a texture into the same level of a texture of the same size, through the framebuffer. */
    private void copy(int from, int to, int level) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, from, level);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, to);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, 0, 0, width(level), height(level));
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
    }

    /** A texture with the size and the mip levels of the atlas and no filter, whose base level each draw sets to the level it reads. */
    private int texture() {
        int[] out = new int[1];
        withState(framebuffer, () -> {
            out[0] = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, out[0]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, size[2] - 1);
            for (int level = 0; level < size[2]; level++) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, width(level), height(level), 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
        });
        return out[0];
    }

    private int width(int level) {
        return ChromaTexels.levelSize(size[0], level);
    }

    private int height(int level) {
        return ChromaTexels.levelSize(size[1], level);
    }

    /**
     * Runs GL work and then puts back all GL state that it can change: the attribute stack holds the enables, the
     * viewport and the masks that GlStateManager keeps a copy of, and the textures of units 0 and 1 (the lightmap), the
     * active unit, the framebuffer and the program are put back by hand, because some drivers pop only the active unit.
     */
    static void withState(int framebuffer, Runnable work) {
        int framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int programBefore = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int unitBefore = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(unitBefore);
        // The attribute stack keeps the draw and read buffers of the bound framebuffer, and GL_BACK of the window (OptiFine
        // Fast Render draws with no framebuffer) is not valid for the framebuffer of the pass, so the push and the pop use it.
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            work.run();
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glPopAttrib();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferBefore);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(unitBefore);
            GL20.glUseProgram(programBefore);
        }
    }

    /** The width, height and number of mip levels of a GL texture, with the framebuffer of the pass or 0. */
    static int[] sizeOf(int framebuffer, int texture) {
        int[] out = new int[3];
        withState(framebuffer, () -> {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            out[0] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            out[1] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            out[2] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) + 1;
        });
        out[2] = ChromaTexels.mipLevels(out[0], out[1], out[2]);
        return out;
    }
}
