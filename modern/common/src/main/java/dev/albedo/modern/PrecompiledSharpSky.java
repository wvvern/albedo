package dev.albedo.modern;

//? if >=1.21.11 && <26.1 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

// Draws the custom sky with the sky shader for each screen pixel on 1.21.11, so that it is sharp at each resolution and
// field of view, from pipelines that the game compiles from the complete shader text of the mod.
final class PrecompiledSharpSky {
    // The state of the compile of one pipeline.
    private enum Compile { NOT_STARTED, READY, FAILED }

    // A pipeline whose shaders come from the jar of the mod, because the game does not load the shaders of a mod as resources.
    private static final class Pipeline {
        final RenderPipeline pipeline;
        final String name;
        final String fragment;
        final String steps;
        Compile state = Compile.NOT_STARTED;

        // The game keeps a compiled shader by its id, so the fragment shader of each sky gets its own id: the name of its file and n.
        Pipeline(int n, String name, String fragment, String steps) {
            this.name = name;
            this.fragment = fragment;
            this.steps = steps;
            this.pipeline = builder(n, name, fragment, SkyShaderSource.readsTerms(steps)).build();
        }

        // Gives the pipeline to the game before each use, because a resource reload empties the pipeline cache, and returns true when it compiled.
        boolean ready() {
            if (state == Compile.FAILED) return false;
            if (RenderSystem.getDevice().precompilePipeline(pipeline,
                    (id, type) -> source(type == ShaderType.VERTEX ? name : fragment, type, steps)).isValid()) {
                if (state == Compile.NOT_STARTED) Albedo.get().platform().info("Compiled the sharp sky shader " + pipeline.getLocation());
                state = Compile.READY;
                return true;
            }
            state = Compile.FAILED;
            Albedo.get().platform().warn("Cannot compile the sharp sky shader " + pipeline.getLocation() + ". The sky draws from the baked image.", null);
            return false;
        }
    }

    // The term images of a sky as one half float texture with the images one under the other.
    private static final class Terms implements AutoCloseable {
        final GpuTexture texture;
        final GpuTextureView view;

        private Terms(GpuTexture texture, GpuTextureView view) {
            this.texture = texture;
            this.view = view;
        }

        // Sends the term images of a sky to a new texture, which has one texel of 0 when the sky has no terms.
        static Terms upload(SkyImage img, String label) {
            int width = img.termTextureWidth(), height = img.termTextureHeight();
            GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, TextureFormat.RGBA8, width, height, 1, 1);
            // The game has no half float format, so the GL gets the level again in half floats on the texture that this write binds.
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, ONE_TEXEL, NativeImage.Format.RGBA, 0, 0, 0, 0, 1, 1);
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
            return new Terms(texture, RenderSystem.getDevice().createTextureView(texture));
        }

        // Binds the texture to the sampler of the sky shader, with linear filters, and with wrap only around the sky.
        void bind(RenderPass pass) {
            pass.bindTexture("AlbedoTerms", view, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false));
        }

        @Override
        public void close() {
            view.close();
            texture.close();
        }
    }

    // The number of pipelines, which gives each pipeline its own name.
    private static int pipelines;

    // The pipeline of each sky by its layer steps, which keeps its id and its compiled program, because the device frees a compiled
    // pipeline only when a resource reload empties its whole cache, so a sky that comes back compiles nothing.
    private static final Map<String, Pipeline> SHARP = new HashMap<String, Pipeline>();
    private static final Map<String, Pipeline> EQUIRECT = new HashMap<String, Pipeline>();

    // Black on the sphere with no blend where the depth is still the far depth of the clear, which resets only the sky of this level view.
    private static final Pipeline BLACK = new Pipeline(pipelines++, SkyShaderSource.SHARP, SkyShaderSource.BLACK, "");

    // The pipeline number n of a sky shader file and its fragment shader file, with the transform blocks for the sky sphere, and the
    // term sampler when the shader reads it.
    private static RenderPipeline.Builder builder(int n, String name, String fragment, boolean terms) {
        RenderPipeline.Builder b = RenderPipeline.builder();
        if (name.equals(SkyShaderSource.SHARP)) {
            b.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER);
        }
        // The game warns about a sampler that the program does not use.
        if (terms) b.withSampler("AlbedoTerms");
        // The sky sphere adds its color to a black frame only where the depth is still the far depth of the clear.
        if (fragment.equals(SkyShaderSource.BLACK)) b.withoutBlend().withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST);
        else if (name.equals(SkyShaderSource.SHARP)) b.withBlend(BlendFunction.ADDITIVE).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST);
        else b.withoutBlend().withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        if (!fragment.equals(SkyShaderSource.BLACK)) b.withUniform("AlbedoSky", UniformType.UNIFORM_BUFFER);
        return b.withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/" + fragment.replace('/', '_') + "_" + n))
                .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, name))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, fragment + "/" + n))
                .withDepthWrite(false)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES);
    }

    // The bytes of the block of a frame for writeFrame, which the write copies at once, so one buffer serves all frames.
    private static final ByteBuffer FRAME_BYTES = MemoryUtil.memAlloc(SkyProgram.FLOATS * 4);
    // One texel of 0 for the write that binds a new term texture.
    private static final ByteBuffer ONE_TEXEL = MemoryUtil.memCalloc(4);
    private static GpuBuffer block;
    private static Terms terms;
    private static long blockSerial = -1;
    // The pipeline of the image of the last prepare, or null when the baked texture must draw it.
    private static Pipeline ready;

    private PrecompiledSharpSky() {}

    // The complete text of a shader file of the mod, with the sky include and its layer steps in it.
    private static String source(String name, ShaderType type, String steps) {
        try {
            return SkyShaderSource.classic(name, type == ShaderType.VERTEX, SkyShaderSource.Dialect.BLOCKS_330, steps);
        } catch (IOException e) {
            Albedo.get().platform().warn("Cannot read the sky shader " + name, e);
            return null;
        }
    }

    // Compiles the pipeline and sends the numbers and the term images of a new image to the GPU, before the sky pass opens, and returns true when the sky shader draws this image.
    static boolean prepare(SkyImage img) {
        Pipeline sharp = ModernSky.wantsSharp(img) ? SHARP.computeIfAbsent(img.program().shaderSteps(),
                steps -> new Pipeline(pipelines++, SkyShaderSource.SHARP, SkyShaderSource.SHARP, steps)) : null;
        ready = sharp != null && sharp.ready() && BLACK.ready() ? sharp : null;
        if (ready == null) return false;
        if (blockSerial != img.serial()) {
            if (block != null) block.close();
            if (terms != null) terms.close();
            block = upload(img.program().block(), "Albedo sky block");
            terms = Terms.upload(img, "Albedo sky terms");
            blockSerial = img.serial();
        }
        SkyTurn turn = ModernSky.turn(img);
        if (img.program().moves() || !turn.still()) writeFrame(block, img.program().at(SkyClock.longSeconds(), turn));
        return true;
    }

    // Writes the block of a frame, with the time of the layers that move and the turn of the sky, outside a render pass.
    private static void writeFrame(GpuBuffer target, float[] values) {
        FRAME_BYTES.asFloatBuffer().put(values);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(target.slice(0L, SkyProgram.FLOATS * 4L), FRAME_BYTES);
    }

    // Draws the image of the last prepare sharp in a pass that has the depth of the frame.
    static void draw(RenderPass pass, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        pass.setPipeline(ready.pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("AlbedoSky", block);
        terms.bind(pass);
        pass.setVertexBuffer(0, sphere);
        pass.draw(0, vertexCount);
    }

    // Draws black where the frame has the far depth, so that the sharp sky can add its color there.
    static void drawBlack(RenderPass pass, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        pass.setPipeline(BLACK.pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, sphere);
        pass.draw(0, vertexCount);
    }

    // A uniform buffer with a block of a program, which writeFrame can change.
    private static GpuBuffer upload(float[] values, String label) {
        ByteBuffer bytes = MemoryUtil.memAlloc(values.length * 4);
        try {
            bytes.asFloatBuffer().put(values);
            return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    // Draws the program and the term images of a sky repeat times into an image of this size, the equirectangular image when view is
    // null, then gives the pixels and the GPU time of one draw, and returns false when the shader cannot compile; values is the block
    // for the draw and png may be null.
    static boolean drawEquirect(SkyImage sky, float[] values, int width, int height, int repeat, float[] view, File png, Harness.SkyDone done) {
        Pipeline equirect = EQUIRECT.computeIfAbsent(sky.program().shaderSteps(),
                steps -> new Pipeline(pipelines++, SkyShaderSource.EQUIRECT, SkyShaderSource.EQUIRECT, steps));
        if (!equirect.ready()) return false;
        TextureTarget target = new TextureTarget("Albedo sky compare", width, height, false);
        GpuBuffer uniforms = upload(values, "Albedo sky compare block");
        Terms skyTerms = Terms.upload(sky, "Albedo sky compare terms");
        GpuBuffer quad = quad(view);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuQuery timer = null;
        try {
            try {
                timer = encoder.timerQueryBegin();
            } catch (IllegalStateException e) {
                Harness.log("! the GPU timer is in use: " + e.getMessage());
            }
            for (int i = 0; i < repeat; i++) {
                try (RenderPass pass = encoder.createRenderPass(() -> "Albedo sky compare", target.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(equirect.pipeline);
                    pass.setUniform("AlbedoSky", uniforms);
                    skyTerms.bind(pass);
                    pass.setVertexBuffer(0, quad);
                    pass.draw(0, 6);
                }
            }
            if (timer != null) encoder.timerQueryEnd(timer);
        } catch (RuntimeException e) {
            if (timer != null) timer.close();
            target.destroyBuffers();
            uniforms.close();
            skyTerms.close();
            quad.close();
            throw e;
        }
        GpuQuery query = timer;
        Screenshot.takeScreenshot(target, image -> {
            int[] argb = new int[width * height];
            NativeImagePixels pixels = new NativeImagePixels(image);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) argb[y * width + x] = pixels.getArgb(x, y);
            }
            if (png != null) {
                try {
                    image.writeToFile(png);
                } catch (IOException e) {
                    Harness.log("! cannot save " + png + ": " + e);
                }
            }
            image.close();
            OptionalLong t = query == null ? OptionalLong.empty() : query.getValue();
            // The screenshot can come back before the GPU has the time, as on NeoForge, so the GL then waits for the draws to end.
            if (query != null && !t.isPresent()) {
                GL11.glFinish();
                t = query.getValue();
            }
            long nanos = t.isPresent() ? t.getAsLong() / repeat : -1;
            if (query != null) query.close();
            target.destroyBuffers();
            uniforms.close();
            skyTerms.close();
            quad.close();
            done.accept(argb, nanos);
        });
        return true;
    }

    // Two triangles over the whole image, with texture coordinates from the top left corner and the sky directions of a view, or null.
    private static GpuBuffer quad(float[] view) {
        float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        int[] corner = {0, 1, 2, 0, 2, 3};
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(corners.length * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < corners.length; i++) {
                int c = corner[i] * 3;
                if (view == null) b.addVertex(0, 0, 0).setUv(corners[i][0], corners[i][1]);
                else b.addVertex(view[c], view[c + 1], view[c + 2]).setUv(corners[i][0], corners[i][1]);
            }
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Albedo sky compare quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }
}
*///?}
