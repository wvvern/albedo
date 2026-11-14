package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.dev.GlReadback;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HarnessLog;
import dev.albedo.core.shader.ShaderSource;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyLayerSteps;
import dev.albedo.core.sky.SkyMesh;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
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
 * baked texture draws the sky; the twin of the modern FixedSharpSky.
 */
final class LegacySharpSky {
    private static final SkyProgram.Uniform[] UNIFORMS = SkyProgram.Uniform.values();

    /** The block of a sky program as the four uniform arrays of the shader, which {@link #set} fills again for each draw. */
    private static final class Block {
        final FloatBuffer[] parts = new FloatBuffer[UNIFORMS.length];

        Block() {
            for (SkyProgram.Uniform u : UNIFORMS) parts[u.ordinal()] = BufferUtils.createFloatBuffer(u.length());
        }

        Block set(float[] b) {
            for (SkyProgram.Uniform u : UNIFORMS) {
                FloatBuffer part = parts[u.ordinal()];
                part.clear();
                part.put(b, u.from(), u.length()).flip();
            }
            return this;
        }
    }

    /** A GL program from the classic sky shaders, and the places of its uniforms. */
    private static final class Program extends LegacyGlProgram {
        final int[] parts = new int[UNIFORMS.length];
        int terms;
        int color;

        Program(String name, String steps) {
            super(name, steps, dialect(), "sharp sky shader", "The sky draws from the baked image.");
        }

        @Override
        void linked() {
            for (SkyProgram.Uniform u : UNIFORMS) parts[u.ordinal()] = uniform(u.glslName());
            terms = uniform("AlbedoTerms");
            color = uniform("ColorModulator");
        }

        /** Puts the program in use with the numbers of a sky, its term texture on unit 0 and a brightness. */
        void use(Block block, int termTexture, float brightness) {
            GL20.glUseProgram(id());
            for (int i = 0; i < parts.length; i++) GL20.glUniform4(parts[i], block.parts[i]);
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
            steps -> new Program(SkyLayerSteps.SHARP, steps), Program::close);
    private static final SkyShaderCache<Program> EQUIRECT = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyLayerSteps.EQUIRECT, steps), Program::close);

    /** The bytes of one RGBA texel of half floats. */
    private static final int HALF_RGBA_BYTES = 8;
    /** The term images of the last image on the GPU. */
    private static final class TermTexture {
        private long serial = -1;
        private int id;

        /** Sends the term images of a new image to the GPU, and returns the texture. */
        int ensure(SkyImage img) {
            if (serial != img.serial()) {
                close();
                id = uploadTerms(img);
                serial = img.serial();
            }
            return id;
        }

        void close() {
            if (id != 0) GlStateManager.deleteTexture(id);
            id = 0;
            serial = -1;
        }
    }

    /** The sky of one frame that the sky shader draws: the program, the term texture and the block. */
    static final class Prepared {
        private final Program program;
        private final int terms;
        private final float[] block;

        private Prepared(Program program, int terms, float[] block) {
            this.program = program;
            this.terms = terms;
            this.block = block;
        }
    }

    private static final Block BLOCK = new Block();
    private static final TermTexture TERMS = new TermTexture();

    private LegacySharpSky() {}

    /**
     * The GLSL dialect that the GPU can compile, or null with a log line when it has none with unsigned integers and half
     * float textures.
     */
    private static ShaderSource.Dialect dialect() {
        ContextCapabilities caps = GLContext.getCapabilities();
        ShaderSource.Dialect dialect = ShaderSource.Dialect.sharp(caps.OpenGL30, caps.GL_EXT_gpu_shader4, caps.GL_ARB_texture_float,
                caps.GL_ARB_half_float_pixel);
        if (dialect == null) Albedo.get().platform().info(ShaderSource.Dialect.NO_SHARP_DIALECT);
        return dialect;
    }

    /**
     * Compiles the program, sends the term images of a new image to the GPU and takes the block of this frame, in a sky
     * with this turn.
     *
     * @return the sky for {@link #draw}, or null when the baked texture must draw this image
     */
    static Prepared prepare(SkyImage img, SkyTurn turn) {
        Program sharp = img.drawsSharp() ? SHARP.get(img.program().shaderSteps()) : null;
        if (sharp == null || !sharp.ready()) return null;
        int terms = TERMS.ensure(img);
        return new Prepared(sharp, terms, img.program().at(SkyClock.longSeconds(), turn).clone());
    }

    /**
     * Adds a prepared sky on the sphere display list to a black frame, only where the depth is still the far depth of the
     * clear, and leaves the blend and the depth write off.
     */
    static void draw(Prepared sky, int sphere, float brightness) {
        sky.program.use(BLOCK.set(sky.block), sky.terms, brightness);
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
        LegacyOffscreen target = LegacyOffscreen.open(width, height);
        int texture = uploadTerms(sky);
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        equirect.use(new Block().set(values), texture, 1f);
        long nanos = timedDraws(repeat, view);
        GL20.glUseProgram(0);
        IntBuffer pixels = target.readBgra();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        target.close();
        GlStateManager.deleteTexture(texture);
        int[] argb = GlReadback.argbTopDown(pixels, width, height);
        if (png != null) save(argb, width, height, png);
        done.accept(argb, nanos);
        return true;
    }

    /** Draws the quad of a view {@code repeat} times and gives the GPU time of one draw, or -1 when the GPU has no timer query. */
    private static long timedDraws(int repeat, float[] view) {
        LegacyGpuTimer timer = new LegacyGpuTimer();
        timer.begin();
        for (int i = 0; i < repeat; i++) Game.drawTriangles(SkyMesh.quad(view));
        timer.end();
        long nanos = LegacyGpuTimer.supported() ? timer.waitNanos() / repeat : -1;
        timer.close();
        return nanos;
    }

    private static void save(int[] argb, int width, int height, File png) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        try {
            ImageIO.write(image, "png", png);
        } catch (IOException e) {
            HarnessLog.fail("cannot save " + png + ": " + e);
        }
    }
}
