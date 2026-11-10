package dev.albedo.modern;

//? if <1.21.11 {
/*import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyShaderSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

// Draws the custom sky with the sky shader for each screen pixel on 1.21.1, so that it is sharp at each resolution and
// field of view, with its own GL program, because a shader instance of the game must load its files as resources.
final class GlSharpSky {
    private static final SkyShaderSource.Dialect DIALECT = SkyShaderSource.Dialect.UNIFORMS_150;

    // The state of the compile of one program.
    private enum Compile { NOT_STARTED, READY, FAILED }

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
        int modelView;
        int projection;
        int color;

        Program(String name, String steps) {
            this.name = name;
            this.steps = steps;
        }

        // Compiles the program on its first use, and returns true when it compiled.
        boolean ready() {
            if (state == Compile.NOT_STARTED) compile();
            return state == Compile.READY;
        }

        // Deletes the GL program.
        void close() {
            if (state == Compile.READY) GL20.glDeleteProgram(id);
            state = Compile.FAILED;
        }

        private void compile() {
            int program = GL20.glCreateProgram();
            int vertex = 0;
            int fragment = 0;
            try {
                vertex = shader(GL20.GL_VERTEX_SHADER, SkyShaderSource.classic(name, true, DIALECT, steps));
                fragment = shader(GL20.GL_FRAGMENT_SHADER, SkyShaderSource.classic(name, false, DIALECT, steps));
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                // The vertex buffers of the game put the position in attribute 0 and the texture coordinates in attribute 1.
                GL20.glBindAttribLocation(program, 0, "Position");
                GL20.glBindAttribLocation(program, 1, "UV0");
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
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
            modelView = GL20.glGetUniformLocation(program, "ModelViewMat");
            projection = GL20.glGetUniformLocation(program, "ProjMat");
            color = GL20.glGetUniformLocation(program, "ColorModulator");
            state = Compile.READY;
            Albedo.get().platform().info("Compiled the sharp sky shader " + name);
        }

        // Puts the program in use with the numbers of a sky, its term texture on unit 0 and a brightness.
        void use(Block block, int termTexture, float brightness) {
            GlStateManager._glUseProgram(id);
            GL20.glUniform4fv(header, block.header);
            GL20.glUniform4fv(world, block.world);
            GL20.glUniform4fv(layers, block.layers);
            GL20.glUniform4fv(ring, block.ring);
            GL20.glUniform4f(color, brightness, brightness, brightness, 1f);
            GL20.glUniform1i(terms, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(termTexture);
        }
    }

    // The programs of each sky that came recently.
    private static final int CACHE = 4;
    private static final SkyShaderCache<Program> SHARP = new SkyShaderCache<Program>(CACHE, steps -> new Program(SkyShaderSource.SHARP, steps),
            Program::close);
    private static final SkyShaderCache<Program> EQUIRECT = new SkyShaderCache<Program>(CACHE,
            steps -> new Program(SkyShaderSource.EQUIRECT, steps), Program::close);

    private static final Block BLOCK = new Block();
    private static int termTexture;
    private static long blockSerial = -1;
    // The program of the image of the last prepare, or null when the baked texture must draw it.
    private static Program ready;

    private GlSharpSky() {}

    // Compiles the program, sends the term images of a new image to the GPU and takes the block of this frame, and returns true when the sky shader draws this image.
    static boolean prepare(SkyImage img) {
        Program sharp = ModernSky.wantsSharp(img) ? SHARP.get(img.program().shaderSteps()) : null;
        ready = sharp != null && sharp.ready() ? sharp : null;
        if (ready == null) return false;
        if (blockSerial != img.serial()) {
            if (termTexture != 0) GlStateManager._deleteTexture(termTexture);
            termTexture = uploadTerms(img);
            blockSerial = img.serial();
        }
        BLOCK.set(img.program().at(SkyClock.longSeconds(), ModernSky.turn(img)));
        return true;
    }

    // Adds the image of the last prepare on the sphere to a black frame, only where the depth is still the far depth of the clear, and leaves the depth write off.
    static void draw(VertexBuffer sphere, Matrix4f modelView, Matrix4f projection, float brightness) {
        ready.use(BLOCK, termTexture, brightness);
        GL20.glUniformMatrix4fv(ready.modelView, false, modelView.get(new float[16]));
        GL20.glUniformMatrix4fv(ready.projection, false, projection.get(new float[16]));
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        sphere.bind();
        sphere.draw();
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        release();
    }

    // Draws black on the sphere at the far depth with no blend, from an opaque texture times a black color, so that only the
    // pixels that still have the far depth of the clear become black.
    static void drawBlack(VertexBuffer sphere, int opaqueTexture, Matrix4f modelView, Matrix4f projection) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, opaqueTexture);
        RenderSystem.setShaderColor(0f, 0f, 0f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        // The game never changes the depth range, so the sphere gets the far depth here and the range goes back to the full range after it.
        GL11.glDepthRange(1.0, 1.0);
        sphere.bind();
        sphere.drawWithShader(modelView, projection, RenderSystem.getShader());
        VertexBuffer.unbind();
        GL11.glDepthRange(0.0, 1.0);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // Takes the program out of use through a shader instance, because an instance skips glUseProgram when it thinks that its program is still in use.
    private static void release() {
        ShaderInstance any = GameRenderer.getPositionTexShader();
        if (any != null) any.clear();
        else GlStateManager._glUseProgram(0);
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

    // A new half float texture with the term images of a sky one under the other, or one texel of 0 when the sky has no terms.
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
        int texture = uploadTerms(sky);
        VertexBuffer quad = quad(view);
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        equirect.use(new Block().set(values), texture, 1f);
        boolean timed = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query;
        int query = timed ? GL15.glGenQueries() : 0;
        if (timed) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        quad.bind();
        for (int i = 0; i < repeat; i++) quad.draw();
        VertexBuffer.unbind();
        long nanos = -1;
        if (timed) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            nanos = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT) / repeat;
            GL15.glDeleteQueries(query);
        }
        release();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        int[] argb = new int[width * height];
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            NativeImagePixels pixels = new NativeImagePixels(image);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) argb[y * width + x] = pixels.getArgb(x, y);
            }
            if (png != null) image.writeToFile(png);
        } catch (IOException e) {
            Harness.log("! cannot save " + png + ": " + e);
        }
        target.destroyBuffers();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        GlStateManager._deleteTexture(texture);
        quad.close();
        done.accept(argb, nanos);
        return true;
    }

    // Two triangles over the whole image, with texture coordinates from the top left corner and the sky directions of a view, or null.
    private static VertexBuffer quad(float[] view) {
        float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        int[] corner = {0, 1, 2, 0, 2, 3};
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < corners.length; i++) {
            int c = corner[i] * 3;
            if (view == null) b.addVertex(0, 0, 0).setUv(corners[i][0], corners[i][1]);
            else b.addVertex(view[c], view[c + 1], view[c + 2]).setUv(corners[i][0], corners[i][1]);
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(b.buildOrThrow());
        VertexBuffer.unbind();
        return buffer;
    }
}
*///?}
