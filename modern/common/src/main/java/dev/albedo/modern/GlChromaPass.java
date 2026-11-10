package dev.albedo.modern;

//? if <1.21.11 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
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

import java.io.IOException;
import java.nio.ByteBuffer;

// The Chroma pass on 1.21.1: its own GL program draws into each mip level of an atlas through a framebuffer, and puts
// back each GL state that it changes, through GlStateManager where the game keeps a copy of the state.
final class GlChromaPass implements ChromaBackend {
    private static final SkyShaderSource.Dialect DIALECT = SkyShaderSource.Dialect.UNIFORMS_150;

    private enum Compile { NOT_STARTED, READY, FAILED }

    private static Compile state = Compile.NOT_STARTED;
    private static int program;
    private static int turnsUniform;
    // Two framebuffers for the copies, the second also for the draws, and an empty vertex array for the triangle.
    private static int readFbo;
    private static int drawFbo;
    private static int vertexArray;

    @Override
    public Shader compile() {
        if (state == Compile.NOT_STARTED) compileNow();
        return state == Compile.READY ? Shader.READY : Shader.FAILED;
    }

    private static void compileNow() {
        int p = GL20.glCreateProgram();
        int vertex = 0;
        int fragment = 0;
        try {
            vertex = shader(GL20.GL_VERTEX_SHADER, SkyShaderSource.classic(SkyShaderSource.CHROMA, true, DIALECT));
            fragment = shader(GL20.GL_FRAGMENT_SHADER, SkyShaderSource.classic(SkyShaderSource.CHROMA, false, DIALECT));
            GL20.glAttachShader(p, vertex);
            GL20.glAttachShader(p, fragment);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) throw new IllegalStateException(GL20.glGetProgramInfoLog(p));
        } catch (IOException | IllegalStateException e) {
            GL20.glDeleteProgram(p);
            state = Compile.FAILED;
            Albedo.get().platform().warn("Cannot compile the Chroma shader " + SkyShaderSource.CHROMA + ". The Chroma colors stay still.", e);
            return;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
        program = p;
        turnsUniform = GL20.glGetUniformLocation(p, "ChromaTurns");
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager._glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "AlbedoPristine"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "AlbedoTexels"), 1);
        GlStateManager._glUseProgram(previous);
        readFbo = GlStateManager.glGenFramebuffers();
        drawFbo = GlStateManager.glGenFramebuffers();
        vertexArray = GlStateManager._glGenVertexArrays();
        state = Compile.READY;
        Albedo.get().platform().info("Compiled the Chroma shader " + SkyShaderSource.CHROMA);
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

    @Override
    public int[] size(TextureAtlas atlas) {
        int[] size = sizeOf(atlas);
        return size[0] > 0 && size[1] > 0 ? size : null;
    }

    // The width, height and number of mip levels of the GL texture of an atlas, on texture unit 0.
    private static int[] sizeOf(TextureAtlas atlas) {
        int active = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(atlas.getId());
        int[] size = {GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT),
            GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) + 1};
        GlStateManager._bindTexture(previous);
        GlStateManager._activeTexture(active);
        // A texture with no mip levels can keep the default largest level of 1000.
        size[2] = Math.max(1, Math.min(size[2], 32 - Integer.numberOfLeadingZeros(Math.max(1, Math.max(size[0], size[1])))));
        return size;
    }

    @Override
    public ChromaBackend.Target create(TextureAtlas atlas, int[] size) {
        return new Target(atlas.getId(), size);
    }

    @Override
    public ChromaBackend.Timer timer() {
        return new Timer();
    }

    // The GL state that the pass changes, as it was before the pass.
    private static final class Saved {
        final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int[] viewport = new int[4];
        final int activeTexture = GlStateManager._getActiveTexture();
        final int texture0;
        final int texture1;
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        Saved() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(texture1);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texture0);
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindVertexArray(vertexArray);
            if (blend) GlStateManager._enableBlend();
            if (depth) GlStateManager._enableDepthTest();
            if (scissor) GlStateManager._enableScissorTest();
            if (cull) GlStateManager._enableCull();
        }
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
                widths[level] = Math.max(1, size[0] >> level);
                heights[level] = Math.max(1, size[1] >> level);
            }
            Saved saved = new Saved();
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
            return atlas.getId() == live && java.util.Arrays.equals(size, sizeOf(atlas));
        }

        @Override
        public void texels(int[][] levels) {
            Saved saved = new Saved();
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
            Saved saved = new Saved();
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            for (int level = 0; level < widths.length; level++) copy(live, pristine, level);
            GlStateManager._glUseProgram(program);
            turnBytes.clear();
            turnBytes.asFloatBuffer().put(turns);
            GL20.nglUniform4fv(turnsUniform, Chroma.TURN_FLOATS / 4, MemoryUtil.memAddress(turnBytes));
            GlStateManager._glBindVertexArray(vertexArray);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawFbo);
            for (int level = 0; level < widths.length; level++) {
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, live, level);
                GlStateManager._viewport(0, 0, widths[level], heights[level]);
                baseLevel(GL13.GL_TEXTURE0, pristine, level);
                baseLevel(GL13.GL_TEXTURE1, texels, level);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            }
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            // A framebuffer cannot read a level below the base level, so the copies at the end of the frame need base level 0.
            baseLevel(GL13.GL_TEXTURE1, texels, 0);
            baseLevel(GL13.GL_TEXTURE0, pristine, 0);
            saved.restore();
        }

        // Binds a texture to a unit with one level as its base level, which the shader reads with texelFetch at level 0.
        private static void baseLevel(int unit, int texture, int level) {
            GlStateManager._activeTexture(unit);
            GlStateManager._bindTexture(texture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, level);
        }

        @Override
        public void restore() {
            Saved saved = new Saved();
            GlStateManager._disableScissorTest();
            for (int level = 0; level < widths.length; level++) copy(pristine, live, level);
            saved.restore();
        }

        // Copies one mip level of a texture into the same level of a texture of the same size, through two framebuffers.
        private void copy(int from, int to, int level) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, from, level);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
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

    // A GL time query around the work of the pass.
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
