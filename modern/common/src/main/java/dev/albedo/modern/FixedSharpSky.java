package dev.albedo.modern;

//? if gl_fixed {
/*import com.mojang.blaze3d.platform.GlStateManager;
import dev.albedo.core.Albedo;
import dev.albedo.core.dev.GlReadback;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HarnessLog;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyMesh;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyShaderSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;

// Draws the custom sky with the sky shader for each screen pixel on the fixed function versions, so that it is sharp at each
// resolution and field of view, with a GL program of its own: GLSL 1.30 with OpenGL 3.0, else GLSL 1.20 with EXT_gpu_shader4,
// else the baked texture draws the sky; the twin of LegacySharpSky.
final class FixedSharpSky {
    private static final SkyProgram.Uniform[] UNIFORMS = SkyProgram.Uniform.values();

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

    // A GL program from the classic sky shaders, and the places of its uniforms.
    private static final class Program extends GlProgram {
        final int[] parts = new int[UNIFORMS.length];
        int terms;
        int color;

        Program(String name, String steps) {
            super(name, name, steps, sharpDialect(), "sharp sky shader", "The sky draws from the baked image.");
        }

        @Override
        boolean supported() {
            if (dialect() != null) return true;
            Albedo.get().platform().info(SkyShaderSource.Dialect.NO_SHARP_DIALECT);
            return false;
        }

        @Override
        void linked() {
            for (SkyProgram.Uniform u : UNIFORMS) parts[u.ordinal()] = uniform(u.glslName());
            terms = uniform("AlbedoTerms");
            color = uniform("ColorModulator");
        }

        // Puts the program in use with the numbers of a sky, its term texture on unit 0 and a brightness.
        void use(Block block, int termTexture, float brightness) {
            GlStateManager._glUseProgram(id());
            for (int i = 0; i < parts.length; i++) GL20.glUniform4fv(parts[i], block.parts[i]);
            // The current color has no effect on a program, so the brightness is a uniform.
            GL20.glUniform4f(color, brightness, brightness, brightness, 1f);
            GL20.glUniform1i(terms, 0);
            // Unit 1 holds the lightmap, so the terms go on unit 0.
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(termTexture);
        }
    }

    // The programs of each sky that came recently.
    private static final SkyShaderCache<Program> SHARP = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyShaderSource.SHARP, steps), Program::close);
    private static final SkyShaderCache<Program> EQUIRECT = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyShaderSource.EQUIRECT, steps), Program::close);

    // The term images of the last image on the GPU.
    private static final class TermTexture {
        private long serial = -1;
        private int id;

        // Sends the term images of a new image to the GPU, and returns the texture.
        int ensure(SkyImage img) {
            if (serial != img.serial()) {
                close();
                id = uploadTerms(img);
                serial = img.serial();
            }
            return id;
        }

        void close() {
            if (id != 0) GlStateManager._deleteTexture(id);
            id = 0;
            serial = -1;
        }
    }

    // The sky of one frame that the sky shader draws: the program, the term texture and the block.
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

    private FixedSharpSky() {}

    // The GLSL dialect that the GPU can compile, or null when it has none with unsigned integers and half float textures.
    private static SkyShaderSource.Dialect sharpDialect() {
        GLCapabilities caps = GL.getCapabilities();
        return SkyShaderSource.Dialect.sharp(caps.OpenGL30, caps.GL_EXT_gpu_shader4, caps.GL_ARB_texture_float, caps.GL_ARB_half_float_pixel);
    }

    // Compiles the program, sends the term images of a new image to the GPU and takes the block of this frame, in a sky with
    // this turn, and returns the sky for draw, or null when the baked texture must draw this image.
    static Prepared prepare(SkyImage img, dev.albedo.core.sky.SkyTurn turn) {
        Program sharp = ModernSky.wantsSharp(img) ? SHARP.get(img.program().shaderSteps()) : null;
        if (sharp == null || !sharp.ready()) return null;
        int terms = TERMS.ensure(img);
        return new Prepared(sharp, terms, img.program().at(SkyClock.longSeconds(), turn).clone());
    }

    // Adds a prepared sky on the sphere display list to a black frame, only where the depth is still the far depth of the
    // clear, and puts back the state that it changed.
    static void draw(Prepared sky, int sphere, float brightness) {
        GlState saved = GlState.ofFixedDraw();
        sky.program.use(BLOCK.set(sky.block), sky.terms, brightness);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GlStateManager._enableBlend();
        GlStateManager._blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager._disableCull();
        GL11.glCallList(sphere);
        saved.restore();
    }

    // A new half float texture with the term images of a sky one under the other, or one texel of 0 when the sky has no terms.
    private static int uploadTerms(SkyImage img) {
        int texture = GlStateManager._genTexture();
        // Unit 1 holds the lightmap, so the terms go on unit 0.
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlTerms.texImage(img);
        GlStateManager._bindTexture(previous);
        GlStateManager._activeTexture(active);
        return texture;
    }

    // Draws triangles of 5 floats for each vertex (x, y, z, u, v) in immediate mode, which a display list can hold.
    static void drawTriangles(float[] v) {
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < v.length; i += 5) {
            GL11.glTexCoord2f(v[i + 3], v[i + 4]);
            GL11.glVertex3f(v[i], v[i + 1], v[i + 2]);
        }
        GL11.glEnd();
    }

    // Draws the program and the term images of a sky repeat times into an image of this size, then gives the pixels and the
    // GPU time of one draw, and returns false when the shader cannot compile.
    static boolean drawEquirect(SkyImage sky, float[] values, int width, int height, int repeat, float[] view, File png, Harness.SkyDone done) {
        if (!GL.getCapabilities().OpenGL30) return false;
        Program equirect = EQUIRECT.get(sky.program().shaderSteps());
        if (!equirect.ready()) return false;
        GlState saved = GlState.ofFixedDraw();
        int framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        Offscreen target = new Offscreen(width, height);
        int texture = uploadTerms(sky);
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._disableAlphaTest();
        equirect.use(new Block().set(values), texture, 1f);
        long nanos = GlTimeQuery.timeEach(repeat, () -> drawTriangles(SkyMesh.quad(view)));
        int[] argb = readArgb(width, height);
        if (alpha) GlStateManager._enableAlphaTest();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        target.close();
        GlStateManager._deleteTexture(texture);
        saved.restore();
        if (png != null) save(argb, width, height, png);
        done.accept(argb, nanos);
        return true;
    }

    // A framebuffer of its own with a color texture of this size, bound with its viewport, so that the render target of the game
    // stays as it is.
    private static final class Offscreen {
        private final int color = GlStateManager._genTexture();
        private final int framebuffer;

        Offscreen(int width, int height) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(color);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            framebuffer = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GL11.glViewport(0, 0, width, height);
        }

        void close() {
            GlStateManager._glDeleteFramebuffers(framebuffer);
            GlStateManager._deleteTexture(color);
        }
    }

    // The opaque pixels of the bound framebuffer from the top row down, as ARGB.
    private static int[] readArgb(int width, int height) {
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        return GlReadback.argbTopDown(pixels, width, height);
    }

    // Writes pixels from the top row down into a PNG file.
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
*///?}
