package dev.albedo.modern;

//? if gl_chroma {
/*import dev.albedo.core.config.Chroma;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.texture.ChromaTexels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
//? if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
//?} else {
/^import com.mojang.blaze3d.platform.GlStateManager;
^///?}

import java.nio.ByteBuffer;
import java.util.Arrays;

// The Chroma pass through the raw GL: its own GL program draws into each mip level of an atlas through a framebuffer, and
// puts back each GL state that it changes; the GPU device before 1.21.11 has no render pass into a mip level above 0.
final class GlChromaPass implements ChromaBackend {
    // The program of the pass, with the pristine copy on unit 0, the Chroma texels on unit 1, two framebuffers for the
    // copies, the second also for the draws, and an empty vertex array for the triangle.
    private static final class Program extends GlProgram {
        int turns;
        int readFbo;
        int drawFbo;
        int vertexArray;

        Program() {
            super(SkyShaderSource.CHROMA, SkyShaderSource.CHROMA, "", SkyShaderSource.Dialect.UNIFORMS_150, "Chroma shader", "Chroma is off.");
        }

        @Override
        void linked() {
            turns = uniform("ChromaTurns");
            int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GlStateManager._glUseProgram(id());
            GL20.glUniform1i(uniform("AlbedoPristine"), 0);
            GL20.glUniform1i(uniform("AlbedoTexels"), 1);
            GlStateManager._glUseProgram(previous);
            readFbo = GlStateManager.glGenFramebuffers();
            drawFbo = GlStateManager.glGenFramebuffers();
            vertexArray = GlStateManager._glGenVertexArrays();
        }
    }

    private static final Program PROGRAM = new Program();

    @Override
    public ShaderState compile() {
        return PROGRAM.ready() ? ShaderState.READY : ShaderState.FAILED;
    }

    @Override
    public int[] size(TextureAtlas atlas) {
        int[] size = sizeOf(atlas);
        return size[0] > 0 && size[1] > 0 ? size : null;
    }

    // The width, height and number of mip levels of the GL texture of an atlas, on texture unit 0.
    private static int[] sizeOf(TextureAtlas atlas) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(glId(atlas));
        int[] size = {GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT),
            GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) + 1};
        GlStateManager._bindTexture(previous);
        GlStateManager._activeTexture(active);
        size[2] = ChromaTexels.mipLevels(size[0], size[1], size[2]);
        return size;
    }

    // The GL name of the texture of an atlas, or 0 while a reload has not made its GPU texture.
    private static int glId(TextureAtlas atlas) {
        //? if >=1.21.5 {
        GpuTexture texture;
        try {
            texture = atlas.getTexture();
        } catch (IllegalStateException e) {
            return 0;
        }
        if (texture.isClosed()) return 0;
        // The validation layer of NeoForge gives a wrapper of the GL texture.
        while (!(texture instanceof GlTexture)) {
            try {
                texture = (GpuTexture) texture.getClass().getMethod("getRealTexture").invoke(texture);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("the atlas texture is not a GL texture: " + texture.getClass().getName(), e);
            }
        }
        return ((GlTexture) texture).glId();
        //?} else {
        /^return atlas.getId();
        ^///?}
    }

    @Override
    public ChromaBackend.Target create(TextureAtlas atlas, int[] size) {
        return new Target(glId(atlas), size);
    }

    @Override
    public ChromaBackend.Timer timer() {
        return new Timer();
    }

    private static final class Target implements ChromaBackend.Target {
        private final int live;
        private final int[] size;
        private final int pristine;
        private final int texels;
        private final int[] widths;
        private final int[] heights;
        private final ByteBuffer turnBytes = MemoryUtil.memAlloc(Chroma.TURN_FLOATS * 4);

        Target(int live, int[] size) {
            this.live = live;
            this.size = size.clone();
            int levels = size[2];
            widths = new int[levels];
            heights = new int[levels];
            for (int level = 0; level < levels; level++) {
                widths[level] = ChromaTexels.levelSize(size[0], level);
                heights[level] = ChromaTexels.levelSize(size[1], level);
            }
            GlState saved = GlState.ofAll();
            pristine = texture();
            texels = texture();
            saved.restore();
        }

        // A texture with the mip levels of the atlas and no filter, whose base level each draw sets to the level it reads.
        private int texture() {
            int texture = GlStateManager._genTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, widths.length - 1);
            for (int level = 0; level < widths.length; level++) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, widths[level], heights[level], 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            return texture;
        }

        @Override
        public boolean sameTexture(TextureAtlas atlas) {
            // A reload or a change of the mip levels gives the atlas new storage under the same GL name.
            return glId(atlas) == live && Arrays.equals(size, sizeOf(atlas));
        }

        @Override
        public void texels(int[][] levels) {
            GlState saved = GlState.ofAll();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texels);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            for (int level = 0; level < widths.length; level++) {
                ByteBuffer bytes = MemoryUtil.memAlloc(widths[level] * heights[level] * 4);
                try {
                    ChromaTexels.rgba(levels[level], bytes);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, widths[level], heights[level], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
                } finally {
                    MemoryUtil.memFree(bytes);
                }
            }
            saved.restore();
        }

        @Override
        public void recolor(float[] turns) {
            GlState saved = GlState.ofAll();
            try {
                GlStateManager._disableBlend();
                GlStateManager._disableDepthTest();
                GlStateManager._disableScissorTest();
                GlStateManager._disableCull();
                for (int level = 0; level < widths.length; level++) copy(live, pristine, level);
                GlStateManager._glUseProgram(PROGRAM.id());
                turnBytes.clear();
                turnBytes.asFloatBuffer().put(turns);
                GL20.nglUniform4fv(PROGRAM.turns, Chroma.TURN_FLOATS / 4, MemoryUtil.memAddress(turnBytes));
                GlStateManager._glBindVertexArray(PROGRAM.vertexArray);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, PROGRAM.drawFbo);
                for (int level = 0; level < widths.length; level++) {
                    GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, live, level);
                    GlStateManager._viewport(0, 0, widths[level], heights[level]);
                    baseLevel(GL13.GL_TEXTURE0, pristine, level);
                    baseLevel(GL13.GL_TEXTURE1, texels, level);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                }
            } finally {
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
                // A framebuffer cannot read a level below the base level, so the copies at the end of the frame need base level 0.
                baseLevel(GL13.GL_TEXTURE1, texels, 0);
                baseLevel(GL13.GL_TEXTURE0, pristine, 0);
                saved.restore();
            }
        }

        // Binds a texture to a unit with one level as its base level, which the shader reads with texelFetch at level 0.
        private static void baseLevel(int unit, int texture, int level) {
            GlStateManager._activeTexture(unit);
            GlStateManager._bindTexture(texture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, level);
        }

        @Override
        public void restore() {
            GlState saved = GlState.ofAll();
            GlStateManager._disableScissorTest();
            for (int level = 0; level < widths.length; level++) copy(pristine, live, level);
            saved.restore();
        }

        // Copies one mip level of a texture into the same level of a texture of the same size, through two framebuffers.
        private void copy(int from, int to, int level) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, PROGRAM.readFbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, from, level);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, PROGRAM.drawFbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, to, level);
            int w = widths[level], h = heights[level];
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        }

        @Override
        public long bytes() {
            long out = 0;
            for (int level = 0; level < widths.length; level++) out += ChromaTexels.levelBytes(widths[level], heights[level]);
            return out;
        }

        @Override
        public void close() {
            GlStateManager._deleteTexture(pristine);
            GlStateManager._deleteTexture(texels);
            MemoryUtil.memFree(turnBytes);
        }
    }

    private static final class Timer implements ChromaBackend.Timer {
        private final int query = GL15.glGenQueries();

        @Override
        public void begin() {
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        }

        @Override
        public void end() {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        }

        @Override
        public long nanos() {
            if (GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) return -1;
            return GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
        }

        @Override
        public void close() {
            GL15.glDeleteQueries(query);
        }
    }
}
*///?}
