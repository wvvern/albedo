package dev.albedo.modern;

//? if gl_fixed {
/*import com.mojang.blaze3d.platform.GlStateManager;
import dev.albedo.core.Albedo;
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
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
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

    // A GL program from the classic sky shaders, and the places of its uniforms.
    private static final class Program extends GlProgram {
        int header;
        int world;
        int layers;
        int ring;
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
            header = uniform("SkyHeader");
            world = uniform("SkyWorld");
            layers = uniform("SkyLayers");
            ring = uniform("SkyRing");
            terms = uniform("AlbedoTerms");
            color = uniform("ColorModulator");
        }

        // Puts the program in use with the numbers of a sky, its term texture on unit 0 and a brightness.
        void use(Block block, int termTexture, float brightness) {
            GlStateManager._glUseProgram(id());
            GL20.glUniform4fv(header, block.header);
            GL20.glUniform4fv(world, block.world);
            GL20.glUniform4fv(layers, block.layers);
            GL20.glUniform4fv(ring, block.ring);
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

    private static final Block BLOCK = new Block();
    private static int termTexture;
    private static long blockSerial = -1;
    // The program of the image of the last prepare, or null when the baked texture must draw it.
    private static Program ready;

    private FixedSharpSky() {}

    // The GLSL dialect that the GPU can compile, or null when it has none with unsigned integers and half float textures.
    private static SkyShaderSource.Dialect sharpDialect() {
        GLCapabilities caps = GL.getCapabilities();
        return SkyShaderSource.Dialect.sharp(caps.OpenGL30, caps.GL_EXT_gpu_shader4, caps.GL_ARB_texture_float, caps.GL_ARB_half_float_pixel);
    }

    // Compiles the program, sends the term images of a new image to the GPU and takes the block of this frame, in a sky with
    // this turn, and returns true when the sky shader draws this image and false when the baked texture must draw it.
    static boolean prepare(SkyImage img, dev.albedo.core.sky.SkyTurn turn) {
        Program sharp = ModernSky.wantsSharp(img) ? SHARP.get(img.program().shaderSteps()) : null;
        ready = sharp != null && sharp.ready() ? sharp : null;
        if (ready == null) return false;
        if (blockSerial != img.serial()) {
            if (termTexture != 0) GlStateManager._deleteTexture(termTexture);
            termTexture = uploadTerms(img);
            blockSerial = img.serial();
        }
        BLOCK.set(img.program().at(SkyClock.longSeconds(), turn));
        return true;
    }

    // Adds the image of the last prepare on the sphere display list to a black frame, only where the depth is still the far
    // depth of the clear, and puts back the state that it changed.
    static void draw(int sphere, float brightness) {
        GlState saved = GlState.ofFixedDraw();
        ready.use(BLOCK, termTexture, brightness);
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
        GLCapabilities caps = GL.getCapabilities();
        if (!caps.OpenGL30) return false;
        Program equirect = EQUIRECT.get(sky.program().shaderSteps());
        if (!equirect.ready()) return false;
        GlState saved = GlState.ofFixedDraw();
        int framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        // A framebuffer of its own, so that the render target of the game stays as it is.
        int color = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(color);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int target = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL11.glViewport(0, 0, width, height);
        int texture = uploadTerms(sky);
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._disableAlphaTest();
        equirect.use(new Block().set(values), texture, 1f);
        boolean timed = caps.OpenGL33 || caps.GL_ARB_timer_query;
        int query = timed ? GL15.glGenQueries() : 0;
        if (timed) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        for (int i = 0; i < repeat; i++) drawTriangles(SkyMesh.quad(view));
        long nanos = -1;
        if (timed) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            nanos = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT) / repeat;
            GL15.glDeleteQueries(query);
        }
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        if (alpha) GlStateManager._enableAlphaTest();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        GlStateManager._glDeleteFramebuffers(target);
        GlStateManager._deleteTexture(color);
        GlStateManager._deleteTexture(texture);
        saved.restore();
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
*///?}
