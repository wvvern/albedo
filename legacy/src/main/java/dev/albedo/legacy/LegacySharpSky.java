package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HarnessLog;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;

/**
 * Draws the custom sky with the sky shader for each screen pixel on 1.8.9 and 1.12.2, so that it is sharp at each resolution and
 * field of view, with an LWJGL 2 GL program: GLSL 1.30 with OpenGL 3.0, else GLSL 1.20 with EXT_gpu_shader4, else the
 * baked texture draws the sky.
 */
final class LegacySharpSky {
    private enum Compile { NOT_STARTED, READY, FAILED }

    /** The block of a sky program as the four uniform arrays of the shader, which {@link #set} fills again for each draw. */
    private static final class Block {
        final FloatBuffer header = BufferUtils.createFloatBuffer(4);
        final FloatBuffer world = BufferUtils.createFloatBuffer(SkyProgram.LAYERS - SkyProgram.WORLD);
        final FloatBuffer layers = BufferUtils.createFloatBuffer(SkyProgram.RING - SkyProgram.LAYERS);
        final FloatBuffer ring = BufferUtils.createFloatBuffer(SkyProgram.FLOATS - SkyProgram.RING);

        Block set(float[] b) {
            fill(header, b, 0);
            fill(world, b, SkyProgram.WORLD);
            fill(layers, b, SkyProgram.LAYERS);
            fill(ring, b, SkyProgram.RING);
            return this;
        }

        private static void fill(FloatBuffer buffer, float[] values, int from) {
            buffer.clear();
            buffer.put(values, from, buffer.capacity()).flip();
        }
    }

    /** A GL program from the classic sky shaders, and the places of its uniforms. */
    private static final class Program {
        final String name;
        final String steps;
        Compile state = Compile.NOT_STARTED;
        int id;
        int header;
        int world;
        int layers;
        int ring;
        int terms;
        int color;

        Program(String name, String steps) {
            this.name = name;
            this.steps = steps;
        }

        /** Compiles the program on its first use, and returns true when it compiled. */
        boolean ready() {
            if (state == Compile.NOT_STARTED) compile();
            return state == Compile.READY;
        }

        void close() {
            if (state == Compile.READY) GL20.glDeleteProgram(id);
            state = Compile.FAILED;
        }

        private void compile() {
            SkyShaderSource.Dialect dialect = dialect();
            if (dialect == null) {
                state = Compile.FAILED;
                Albedo.get().platform().info("The GPU has no OpenGL 3.0 or EXT_gpu_shader4, so the sky draws from the baked image");
                return;
            }
            int program = GL20.glCreateProgram();
            int vertex = 0;
            int fragment = 0;
            try {
                vertex = shader(GL20.GL_VERTEX_SHADER, SkyShaderSource.classic(name, true, dialect, steps));
                fragment = shader(GL20.GL_FRAGMENT_SHADER, SkyShaderSource.classic(name, false, dialect, steps));
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 32768));
                }
            } catch (IOException | IllegalStateException e) {
                GL20.glDeleteProgram(program);
                state = Compile.FAILED;
                Albedo.get().platform().warn("Cannot compile the sharp sky shader " + name + ". The sky draws from the baked image.", e);
                return;
            } finally {
                if (vertex != 0) GL20.glDeleteShader(vertex);
                if (fragment != 0) GL20.glDeleteShader(fragment);
            }
            id = program;
            header = GL20.glGetUniformLocation(program, "SkyHeader");
            world = GL20.glGetUniformLocation(program, "SkyWorld");
            layers = GL20.glGetUniformLocation(program, "SkyLayers");
            ring = GL20.glGetUniformLocation(program, "SkyRing");
            terms = GL20.glGetUniformLocation(program, "AlbedoTerms");
            color = GL20.glGetUniformLocation(program, "ColorModulator");
            state = Compile.READY;
            Albedo.get().platform().info("Compiled the sharp sky shader " + name + " as " + dialect);
        }

        /** Puts the program in use with the numbers of a sky, its term texture on unit 0 and a brightness. */
        void use(Block block, int termTexture, float brightness) {
            GL20.glUseProgram(id);
            GL20.glUniform4(header, block.header);
            GL20.glUniform4(world, block.world);
            GL20.glUniform4(layers, block.layers);
            GL20.glUniform4(ring, block.ring);
            // GlStateManager.color has no effect on a program, so the brightness is a uniform.
            GL20.glUniform4f(color, brightness, brightness, brightness, 1f);
            GL20.glUniform1i(terms, 0);
            // Unit 1 holds the lightmap, so the terms go on unit 0.
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(termTexture);
        }
    }

    /** The programs of each sky that came recently. */
    private static final SkyShaderCache<Program> SHARP = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyShaderSource.SHARP, steps), Program::close);
    private static final SkyShaderCache<Program> EQUIRECT = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyShaderSource.EQUIRECT, steps), Program::close);

    /** The bytes of one RGBA texel of half floats. */
    private static final int HALF_RGBA_BYTES = 8;
    private static final Block BLOCK = new Block();
    private static int termTexture;
    private static long blockSerial = -1;
    /** The program of the image of the last {@link #prepare}, or null when the baked texture must draw it. */
    private static Program ready;

    private LegacySharpSky() {}

    /** The GLSL dialect that the GPU can compile, or null when it has none with unsigned integers and half float textures. */
    private static SkyShaderSource.Dialect dialect() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.OpenGL30) return SkyShaderSource.Dialect.FIXED_130;
        if (caps.GL_EXT_gpu_shader4 && caps.GL_ARB_texture_float && caps.GL_ARB_half_float_pixel) return SkyShaderSource.Dialect.FIXED_120_EXT;
        return null;
    }

    /**
     * Compiles the program, sends the term images of a new image to the GPU and takes the block of this frame, in a sky
     * with this turn.
     *
     * @return true when the sky shader draws this image, false when the baked texture must draw it
     */
    static boolean prepare(SkyImage img, SkyTurn turn) {
        Program sharp = img.drawsSharp() ? SHARP.get(img.program().shaderSteps()) : null;
        ready = sharp != null && sharp.ready() ? sharp : null;
        if (ready == null) return false;
        if (blockSerial != img.serial()) {
            if (termTexture != 0) GlStateManager.deleteTexture(termTexture);
            termTexture = uploadTerms(img);
            blockSerial = img.serial();
        }
        BLOCK.set(img.program().at(SkyClock.longSeconds(), turn));
        return true;
    }

    /**
     * Adds the image of the last {@link #prepare} on the sphere display list to a black frame, only where the depth is
     * still the far depth of the clear, and leaves the blend and the depth write off.
     */
    static void draw(int sphere, float brightness) {
        ready.use(BLOCK, termTexture, brightness);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager.disableCull();
        GlStateManager.callList(sphere);
        GlStateManager.enableCull();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableBlend();
        GL20.glUseProgram(0);
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    /** A new half float texture with the term images of a sky one under the other, or one texel of 0 when the sky has no terms. */
    private static int uploadTerms(SkyImage img) {
        int width = img.termTextureWidth();
        int height = img.termTextureHeight();
        ByteBuffer bytes = BufferUtils.createByteBuffer(width * height * HALF_RGBA_BYTES);
        bytes.asShortBuffer().put(img.terms());
        int texture = GlStateManager.generateTexture();
        // Unit 1 holds the lightmap, so the terms go on unit 0.
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        // GL30 has the same numbers as ARB_texture_float and ARB_half_float_pixel.
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, bytes);
        return texture;
    }

    /**
     * Draws the program and the term images of a sky {@code repeat} times into an image of this size, then gives the
     * pixels and the GPU time of one draw.
     *
     * @param values the block of the program of the sky for this draw
     * @param view the sky directions of the corners of a camera view, or null for the equirectangular image
     * @param png a file for the image, or null
     * @return false when the shader cannot compile
     */
    static boolean drawEquirect(SkyImage sky, float[] values, int width, int height, int repeat, float[] view, File png, Harness.SkyDone done) {
        Program equirect = EQUIRECT.get(sky.program().shaderSteps());
        if (!equirect.ready() || !OpenGlHelper.framebufferSupported) return false;
        int framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        // A framebuffer of its own, because a Framebuffer of the game binds nothing while OptiFine Fast Render is on.
        int color = GlStateManager.generateTexture();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(color);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int target = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL11.glViewport(0, 0, width, height);
        int texture = uploadTerms(sky);
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        equirect.use(new Block().set(values), texture, 1f);
        ContextCapabilities caps = GLContext.getCapabilities();
        boolean timed = caps.OpenGL33 || caps.GL_ARB_timer_query;
        int query = timed ? GL15.glGenQueries() : 0;
        if (timed) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        for (int i = 0; i < repeat; i++) Game.drawTriangles(SkyImage.quad(view));
        long nanos = -1;
        if (timed) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            nanos = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT) / repeat;
            GL15.glDeleteQueries(query);
        }
        GL20.glUseProgram(0);
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferBefore);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        OpenGlHelper.glDeleteFramebuffers(target);
        GlStateManager.deleteTexture(color);
        GlStateManager.deleteTexture(texture);
        // The GL rows go from the bottom up.
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) argb[y * width + x] = pixels.get((height - 1 - y) * width + x) | 0xFF000000;
        }
        if (png != null) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, argb, 0, width);
            try {
                ImageIO.write(image, "png", png);
            } catch (IOException e) {
                HarnessLog.fail("cannot save " + png + ": " + e);
            }
        }
        done.accept(argb, nanos);
        return true;
    }
}
