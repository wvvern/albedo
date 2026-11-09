package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
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
 * Draws the custom sky with the sky shader for each screen pixel on 1.8.9, so that it is sharp at each resolution and
 * field of view, with an LWJGL 2 GL program: GLSL 1.30 with OpenGL 3.0, else GLSL 1.20 with EXT_gpu_shader4, else the
 * baked texture draws the sky.
 */
final class LegacySharpSky {
    /** The state of the compile of one program. */
    private enum Compile { NOT_STARTED, READY, FAILED }

    /** The block of a sky program as the three uniform arrays of the shader. */
    private static final class Block {
        final FloatBuffer header;
        final FloatBuffer layers;
        final FloatBuffer ring;

        Block(SkyProgram program) {
            float[] b = program.block();
            header = buffer(b, 0, SkyProgram.LAYERS);
            layers = buffer(b, SkyProgram.LAYERS, SkyProgram.RING);
            ring = buffer(b, SkyProgram.RING, SkyProgram.FLOATS);
        }

        private static FloatBuffer buffer(float[] values, int from, int to) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(to - from);
            buffer.put(values, from, to - from).flip();
            return buffer;
        }
    }

    /** A GL program from the classic sky shaders, and the places of its uniforms. */
    private static final class Program {
        final String name;
        Compile state = Compile.NOT_STARTED;
        int id;
        int header;
        int layers;
        int ring;
        int terms;
        int time;
        int color;

        Program(String name) {
            this.name = name;
        }

        /** Compiles the program on its first use, and returns true when it compiled. */
        boolean ready() {
            if (state == Compile.NOT_STARTED) compile();
            return state == Compile.READY;
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
                vertex = shader(GL20.GL_VERTEX_SHADER, SkyShaderSource.classic(name, true, dialect));
                fragment = shader(GL20.GL_FRAGMENT_SHADER, SkyShaderSource.classic(name, false, dialect));
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
            layers = GL20.glGetUniformLocation(program, "SkyLayers");
            ring = GL20.glGetUniformLocation(program, "SkyRing");
            terms = GL20.glGetUniformLocation(program, "AlbedoTerms");
            time = GL20.glGetUniformLocation(program, "albedoTime");
            color = GL20.glGetUniformLocation(program, "ColorModulator");
            state = Compile.READY;
            Albedo.get().platform().info("Compiled the sharp sky shader " + name + " as " + dialect);
        }

        /** Puts the program in use with the numbers of a sky, its term texture on unit 0, a brightness and the seconds of the moving layers. */
        void use(Block block, int termTexture, float brightness, float seconds) {
            GL20.glUseProgram(id);
            GL20.glUniform4(header, block.header);
            GL20.glUniform4(layers, block.layers);
            GL20.glUniform4(ring, block.ring);
            // GlStateManager.color has no effect on a program, so the brightness is a uniform.
            GL20.glUniform4f(color, brightness, brightness, brightness, 1f);
            GL20.glUniform1f(time, seconds);
            GL20.glUniform1i(terms, 0);
            // Unit 1 holds the lightmap, so the terms go on unit 0.
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(termTexture);
        }
    }

    private static final Program SHARP = new Program(SkyShaderSource.SHARP);
    private static final Program EQUIRECT = new Program(SkyShaderSource.EQUIRECT);

    private static Block block;
    private static int termTexture;
    private static long blockSerial = -1;

    private LegacySharpSky() {}

    /** The GLSL dialect that the GPU can compile, or null when it has none with unsigned integers and half float textures. */
    private static SkyShaderSource.Dialect dialect() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.OpenGL30) return SkyShaderSource.Dialect.FIXED_130;
        if (caps.GL_EXT_gpu_shader4 && caps.GL_ARB_texture_float && caps.GL_ARB_half_float_pixel) return SkyShaderSource.Dialect.FIXED_120_EXT;
        return null;
    }

    /** Compiles the program and sends the term images of a new image to the GPU. */
    static void prepare(SkyImage img) {
        if (!img.drawsSharp() || !SHARP.ready() || blockSerial == img.serial()) return;
        if (termTexture != 0) GlStateManager.deleteTexture(termTexture);
        block = new Block(img.program());
        termTexture = uploadTerms(img);
        blockSerial = img.serial();
    }

    /**
     * Draws the image sharp on the sphere display list.
     *
     * @return false when the baked texture must draw the sky
     */
    static boolean draw(SkyImage img, int sphere, float brightness) {
        if (!img.drawsSharp() || SHARP.state != Compile.READY || blockSerial != img.serial()) return false;
        SHARP.use(block, termTexture, brightness, SkyClock.seconds());
        GlStateManager.disableCull();
        GlStateManager.callList(sphere);
        GlStateManager.enableCull();
        GL20.glUseProgram(0);
        return true;
    }

    /** A compiled shader of a type, or an exception with the log of the GL. */
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
        int width = img.termTextureWidth(), height = img.termTextureHeight();
        ByteBuffer bytes = BufferUtils.createByteBuffer(width * height * 8);
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
     * Draws the program and the term images of a sky {@code repeat} times into an equirectangular image of this size, then
     * gives the pixels and the GPU time of one draw.
     *
     * @param png a file for the image, or null
     * @return false when the shader cannot compile
     */
    static boolean drawEquirect(SkyImage sky, int width, int height, int repeat, File png, Harness.SkyDone done) {
        if (!EQUIRECT.ready()) return false;
        int texture = uploadTerms(sky);
        Framebuffer target = new Framebuffer(width, height, false);
        target.bindFramebuffer(true);
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        // The bake has no time, so the compare draws the moving layers at time 0.
        EQUIRECT.use(new Block(sky.program()), texture, 1f, 0f);
        ContextCapabilities caps = GLContext.getCapabilities();
        boolean timed = caps.OpenGL33 || caps.GL_ARB_timer_query;
        int query = timed ? GL15.glGenQueries() : 0;
        if (timed) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        for (int i = 0; i < repeat; i++) quad();
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
        target.deleteFramebuffer();
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
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
                Harness.log("! cannot save " + png + ": " + e);
            }
        }
        done.accept(argb, nanos);
        return true;
    }

    /** Two triangles over the whole image, with texture coordinates from the top left corner. */
    private static void quad() {
        float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        WorldRenderer r = Tessellator.getInstance().getWorldRenderer();
        r.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        for (float[] c : corners) r.pos(0, 0, 0).tex(c[0], c[1]).endVertex();
        Tessellator.getInstance().draw();
    }
}
