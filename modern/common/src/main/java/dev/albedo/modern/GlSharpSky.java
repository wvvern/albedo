package dev.albedo.modern;

//? if >=1.17 && <1.21.6 {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyShaderSource;
import org.lwjgl.opengl.GL;
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
//?} else {
/^import com.mojang.blaze3d.platform.GlStateManager;
^///?}

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

// Draws the custom sky with the sky shader for each screen pixel through the raw GL, so that it is sharp at each
// resolution and field of view, and puts back each GL state that it changes.
final class GlSharpSky {
    // The block of a sky program as the four uniform arrays of the shader, which set fills again for each draw.
    private static final class Block {
        final float[] header = new float[4];
        final float[] world = new float[SkyProgram.LAYERS - SkyProgram.WORLD];
        final float[] layers = new float[SkyProgram.RING - SkyProgram.LAYERS];
        final float[] ring = new float[SkyProgram.FLOATS - SkyProgram.RING];

        Block set(float[] b) {
            System.arraycopy(b, 0, header, 0, header.length);
            System.arraycopy(b, SkyProgram.WORLD, world, 0, world.length);
            System.arraycopy(b, SkyProgram.LAYERS, layers, 0, layers.length);
            System.arraycopy(b, SkyProgram.RING, ring, 0, ring.length);
            return this;
        }
    }

    // A GL program from the classic sky shaders, and the places of its uniforms.
    private static final class Program extends GlProgram {
        int header;
        int world;
        int layers;
        int ring;
        int terms;
        int modelView;
        int projection;
        int color;

        Program(String vertex, String fragment, String steps) {
            super(vertex, fragment, steps, "sharp sky shader", "The sky draws from the baked image.");
        }

        @Override
        void linked() {
            header = uniform("SkyHeader");
            world = uniform("SkyWorld");
            layers = uniform("SkyLayers");
            ring = uniform("SkyRing");
            terms = uniform("AlbedoTerms");
            modelView = uniform("ModelViewMat");
            projection = uniform("ProjMat");
            color = uniform("ColorModulator");
        }

        // Puts the program in use with the numbers of a sky, its term texture on unit 0 and a brightness.
        void use(Block block, int termTexture, float brightness) {
            GlStateManager._glUseProgram(id());
            GL20.glUniform4fv(header, block.header);
            GL20.glUniform4fv(world, block.world);
            GL20.glUniform4fv(layers, block.layers);
            GL20.glUniform4fv(ring, block.ring);
            GL20.glUniform4f(color, brightness, brightness, brightness, 1f);
            GL20.glUniform1i(terms, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(termTexture);
        }

        // Puts the program in use with only the transforms of the sky sphere.
        void use(float[] modelViewMatrix, float[] projectionMatrix) {
            GlStateManager._glUseProgram(id());
            GL20.glUniformMatrix4fv(modelView, false, modelViewMatrix);
            GL20.glUniformMatrix4fv(projection, false, projectionMatrix);
        }
    }

    // The programs of each sky that came recently.
    private static final SkyShaderCache<Program> SHARP = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyShaderSource.SHARP, SkyShaderSource.SHARP, steps), Program::close);
    private static final SkyShaderCache<Program> EQUIRECT = new SkyShaderCache<Program>(SkyShaderCache.SIZE,
            steps -> new Program(SkyShaderSource.EQUIRECT, SkyShaderSource.EQUIRECT, steps), Program::close);
    // Black on the sky sphere at the far depth.
    private static final Program BLACK = new Program(SkyShaderSource.SHARP, SkyShaderSource.BLACK, "");

    private static final Block BLOCK = new Block();
    private static int termTexture;
    private static long blockSerial = -1;
    // The program of the image of the last prepare, or null when the baked texture must draw it.
    private static Program ready;

    private GlSharpSky() {}

    // Compiles the program, sends the term images of a new image to the GPU and takes the block of this frame, and returns true when the sky shader draws this image.
    static boolean prepare(SkyImage img) {
        Program sharp = ModernSky.wantsSharp(img) ? SHARP.get(img.program().shaderSteps()) : null;
        ready = sharp != null && sharp.ready() && BLACK.ready() ? sharp : null;
        if (ready == null) return false;
        if (blockSerial != img.serial()) {
            GlState saved = GlState.ofAll();
            if (termTexture != 0) GlStateManager._deleteTexture(termTexture);
            termTexture = uploadTerms(img);
            blockSerial = img.serial();
            saved.restore();
        }
        BLOCK.set(img.program().at(SkyClock.longSeconds(), ModernSky.turn(img)));
        return true;
    }

    // Adds the image of the last prepare on the sphere to a black frame, only where the depth is still the far depth of the clear; the matrices are in column order.
    static void draw(GlMesh sphere, float[] modelView, float[] projection, float brightness) {
        GlState saved = GlState.ofDraw();
        ready.use(BLOCK, termTexture, brightness);
        ready.use(modelView, projection);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager._disableCull();
        sphere.draw();
        saved.restore();
    }

    // Draws black on the sphere at the far depth with no blend, so that only the pixels that still have the far depth of the clear become black.
    static void drawBlack(GlMesh sphere, float[] modelView, float[] projection) {
        GlState saved = GlState.ofDraw();
        BLACK.use(modelView, projection);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GlStateManager._disableBlend();
        GlStateManager._disableCull();
        sphere.draw();
        saved.restore();
    }

    // A new half float texture on unit 0 with the term images of a sky one under the other, or one texel of 0 when the sky has no terms.
    private static int uploadTerms(SkyImage img) {
        int width = img.termTextureWidth(), height = img.termTextureHeight();
        int texture = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer bytes = MemoryUtil.memCalloc(width * height * 8);
        try {
            bytes.asShortBuffer().put(img.terms());
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
        return texture;
    }

    // Draws the program and the term images of a sky repeat times into an image of this size, the equirectangular image when view is
    // null, then gives the pixels and the GPU time of one draw, and returns false when the shader cannot compile; values is the block
    // for the draw and png may be null.
    static boolean drawEquirect(SkyImage sky, float[] values, int width, int height, int repeat, float[] view, File png, Harness.SkyDone done) {
        Program equirect = EQUIRECT.get(sky.program().shaderSteps());
        if (!equirect.ready()) return false;
        GlState saved = GlState.ofAll();
        int texture = uploadTerms(sky);
        GlMesh quad = new GlMesh(SkyImage.quad(view));
        int color = colorTexture(width, height);
        int framebuffer = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GlStateManager._viewport(0, 0, width, height);
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        equirect.use(new Block().set(values), texture, 1f);
        boolean timed = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query;
        int query = timed ? GL15.glGenQueries() : 0;
        if (timed) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        for (int i = 0; i < repeat; i++) quad.draw();
        long nanos = -1;
        if (timed) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            nanos = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT) / repeat;
            GL15.glDeleteQueries(query);
        }
        int[] argb = readPixels(width, height);
        saved.restore();
        GlStateManager._glDeleteFramebuffers(framebuffer);
        GlStateManager._deleteTexture(color);
        GlStateManager._deleteTexture(texture);
        quad.close();
        if (png != null) save(argb, width, height, png);
        done.accept(argb, nanos);
        return true;
    }

    // A new texture on unit 0 for the color of a framebuffer of this size.
    private static int colorTexture(int width, int height) {
        int texture = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return texture;
    }

    // The opaque pixels of the bound framebuffer from the top row down, as ARGB.
    private static int[] readPixels(int width, int height) {
        int[] argb = new int[width * height];
        ByteBuffer bytes = MemoryUtil.memAlloc(width * height * 4);
        try {
            // The game can leave a row length and skips for its own reads.
            GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            for (int y = 0; y < height; y++) {
                // The GL gives the bottom row first.
                int row = (height - 1 - y) * width * 4;
                for (int x = 0; x < width; x++) {
                    int at = row + x * 4;
                    argb[y * width + x] = 0xFF000000 | (bytes.get(at) & 0xFF) << 16 | (bytes.get(at + 1) & 0xFF) << 8 | (bytes.get(at + 2) & 0xFF);
                }
            }
        } finally {
            MemoryUtil.memFree(bytes);
        }
        return argb;
    }

    // Writes pixels from the top row down into a PNG file.
    private static void save(int[] argb, int width, int height, File png) {
        try (NativeImage image = new NativeImage(width, height, false)) {
            NativeImagePixels pixels = new NativeImagePixels(image);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) pixels.setArgb(x, y, argb[y * width + x]);
            }
            image.writeToFile(png);
        } catch (IOException e) {
            Harness.log("! cannot save " + png + ": " + e);
        }
    }
}
*///?}
