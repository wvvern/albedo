package dev.albedo.modern;

//? if gl_fixed {
/*import com.mojang.blaze3d.platform.GlStateManager;
import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.texture.ChromaFrames;
import dev.albedo.core.texture.ChromaTexels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

// The Chroma pass of the fixed function versions with an OpenGL 2.0 program and a framebuffer: it draws the hue of each moving
// texel into each mip level of an atlas, and it puts back all GL state that it can change, with the attribute stack, where
// withState, sizeOf, baseLevel and copy are twins of LegacyChromaTarget and a fix lands in both.
final class FixedChromaPass implements ChromaBackend {
    // The Chroma program: GLSL 1.30 with OpenGL 3.0, else GLSL 1.20.
    private static final class Program extends GlProgram {
        int turns;
        int size;

        Program() {
            super(SkyShaderSource.CHROMA, SkyShaderSource.CHROMA, "", SkyShaderSource.Dialect.chroma(GL.getCapabilities().OpenGL30),
                    "Chroma shader", "Chroma is off.");
        }

        @Override
        boolean supported() {
            GLCapabilities caps = GL.getCapabilities();
            if (caps.OpenGL20 && (caps.OpenGL30 || caps.GL_ARB_framebuffer_object || caps.GL_EXT_framebuffer_object)) return true;
            Albedo.get().platform().warn("The GPU has no OpenGL 2.0 or no framebuffers, so Chroma is off", null);
            return false;
        }

        @Override
        void linked() {
            turns = uniform("ChromaTurns");
            size = uniform("ChromaSize");
            int pristineUniform = uniform("AlbedoPristine");
            int texelsUniform = uniform("AlbedoTexels");
            withState(() -> {
                GL20.glUseProgram(id());
                GL20.glUniform1i(pristineUniform, 0);
                GL20.glUniform1i(texelsUniform, 1);
            });
            framebuffer = GlStateManager.glGenFramebuffers();
        }
    }

    // Null before the first compile, which needs the GL context.
    private static Program program;
    private static int framebuffer;
    private static final FloatBuffer TURNS = BufferUtils.createFloatBuffer(Chroma.TURN_FLOATS);

    @Override
    public ShaderState compile() {
        if (program == null) program = new Program();
        return program.ready() ? ShaderState.READY : ShaderState.FAILED;
    }

    // Runs GL work and then puts back all GL state that it can change: the attribute stack holds the enables, the viewport and
    // the masks that GlStateManager keeps a copy of, and the textures of units 0 and 1 (the lightmap), the active unit, the
    // framebuffer and the program are put back by hand, because some drivers pop only the active unit.
    private static void withState(Runnable work) {
        int framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int programBefore = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int unitBefore = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(unitBefore);
        // The attribute stack keeps the draw and read buffers of the bound framebuffer, and GL_BACK of the window is not valid
        // for the framebuffer of the pass, so the push and the pop use it.
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            work.run();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glPopAttrib();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(unitBefore);
            GL20.glUseProgram(programBefore);
        }
    }

    @Override
    public ChromaFrames.Size size(TextureAtlas atlas) {
        ChromaFrames.Size size = sizeOf(atlas.getId());
        return size.present() ? size : null;
    }

    // The width, height and number of mip levels of the GL texture of an atlas.
    private static ChromaFrames.Size sizeOf(int texture) {
        int[] out = new int[3];
        withState(() -> {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            out[0] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            out[1] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            out[2] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) + 1;
        });
        return ChromaFrames.Size.clamped(out[0], out[1], out[2]);
    }

    @Override
    public ChromaBackend.Target create(TextureAtlas atlas, ChromaFrames.Size size) {
        return new Target(atlas.getId(), size);
    }

    @Override
    public ChromaBackend.Timer timer() {
        return new GlTimeQuery();
    }

    private static final class Target implements ChromaBackend.Target {
        private final int live;
        private final ChromaFrames.Size size;
        private final int[] widths;
        private final int[] heights;
        private final int pristine;
        private final int texels;

        Target(int live, ChromaFrames.Size size) {
            this.live = live;
            this.size = size;
            widths = new int[size.levels];
            heights = new int[size.levels];
            for (int level = 0; level < size.levels; level++) {
                widths[level] = ChromaTexels.levelSize(size.width, level);
                heights[level] = ChromaTexels.levelSize(size.height, level);
            }
            pristine = texture();
            texels = texture();
        }

        // A texture with the size and the mip levels of the atlas and no filter, whose base level each draw sets to the level it reads.
        private int texture() {
            int[] out = new int[1];
            withState(() -> {
                out[0] = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, out[0]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, widths.length - 1);
                for (int level = 0; level < widths.length; level++) {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, widths[level], heights[level], 0, GL11.GL_RGBA,
                            GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                }
            });
            return out[0];
        }

        @Override
        public boolean sameTexture(TextureAtlas atlas) {
            // A reload or a change of the mip levels gives the atlas new storage under the same GL name.
            return atlas.getId() == live && size.equals(sizeOf(live));
        }

        @Override
        public void texels(int[][] levels) {
            withState(() -> {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texels);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                for (int level = 0; level < widths.length; level++) {
                    ByteBuffer bytes = BufferUtils.createByteBuffer(levels[level].length * 4);
                    ChromaTexels.rgba(levels[level], bytes);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, widths[level], heights[level], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
                }
            });
        }

        // Copies each mip level of the atlas into the pristine copy, then draws the colors of this frame into the atlas.
        @Override
        public void recolor(float[] turns) {
            withState(() -> {
                for (int level = 0; level < widths.length; level++) copy(live, pristine, level);
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
                GL20.glUniform4fv(program.turns, TURNS);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                for (int level = 0; level < widths.length; level++) {
                    GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, live, level);
                    GL11.glViewport(0, 0, widths[level], heights[level]);
                    GL20.glUniform2f(program.size, widths[level], heights[level]);
                    baseLevel(GL13.GL_TEXTURE1, texels, level);
                    baseLevel(GL13.GL_TEXTURE0, pristine, level);
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    GL11.glVertex2f(-1, -1);
                    GL11.glVertex2f(3, -1);
                    GL11.glVertex2f(-1, 3);
                    GL11.glEnd();
                }
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
                // A framebuffer cannot read a level below the base level, so the copies at the end of the frame need base level 0.
                baseLevel(GL13.GL_TEXTURE1, texels, 0);
                baseLevel(GL13.GL_TEXTURE0, pristine, 0);
            });
        }

        // Binds a texture to a unit with one level as its base level, which the shader reads as its level 0.
        private static void baseLevel(int unit, int texture, int level) {
            GL13.glActiveTexture(unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, level);
        }

        @Override
        public void restore() {
            withState(() -> {
                for (int level = 0; level < widths.length; level++) copy(pristine, live, level);
            });
        }

        // Copies one mip level of a texture into the same level of a texture of the same size, through the framebuffer.
        private void copy(int from, int to, int level) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, from, level);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, to);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, 0, 0, widths[level], heights[level]);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        }

        @Override
        public long bytes() {
            long out = 0;
            for (int level = 0; level < widths.length; level++) out += ChromaTexels.levelBytes(widths[level], heights[level]);
            return out;
        }

        @Override
        public void close() {
            GL11.glDeleteTextures(pristine);
            GL11.glDeleteTextures(texels);
        }
    }
}
*///?}
