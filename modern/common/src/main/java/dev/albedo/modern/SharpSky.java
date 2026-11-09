package dev.albedo.modern;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Draws the custom sky with the sky shader for each screen pixel, so that it is sharp at each resolution and field of
 * view, when the design, the setting and the GPU permit; else the baked texture draws it.
 */
final class SharpSky {
    /** The state of the background compile of one pipeline. */
    private enum Compile { NOT_STARTED, RUNNING, READY, FAILED }

    /**
     * A pipeline that compiles from a {@link Source} on an executor and is ready on a later call, or in the same call when the
     * executor runs the compile at once.
     */
    static final class Pipeline {
        private final RenderPipeline pipeline;
        private final Executor executor;
        private Compile state = Compile.NOT_STARTED;
        private CompletableFuture<CompiledRenderPipeline.Pending> pending;
        private CompiledRenderPipeline compiled;

        Pipeline(RenderPipeline pipeline, Executor executor) {
            this.pipeline = pipeline;
            this.executor = executor;
        }

        /** Starts or finishes the compile on the render thread, and returns the compiled pipeline once it is ready, else null. */
        CompiledRenderPipeline get() {
            if (state == Compile.NOT_STARTED) start();
            if (state == Compile.RUNNING && pending.isDone()) finish();
            return state == Compile.READY ? compiled : null;
        }

        private void start() {
            state = Compile.RUNNING;
            try {
                pending = RenderSystem.getDevice().compilePipeline(pipeline, new Source(pipeline), executor);
            } catch (IOException | RuntimeException e) {
                fail(e);
            }
        }

        private void finish() {
            try {
                compiled = pending.join().finishCompile();
            } catch (RuntimeException e) {
                fail(e);
                return;
            }
            if (compiled == null) {
                fail(null);
            } else {
                state = Compile.READY;
                Albedo.get().platform().info("Compiled the sky shader " + pipeline.getLocation());
            }
        }

        private void fail(Throwable e) {
            state = Compile.FAILED;
            compiled = null;
            Albedo.get().platform().warn("Cannot compile the sky shader " + pipeline.getLocation() + ". The sky draws in a simpler way.", e);
        }
    }

    /** The term images of a sky on the GPU, as one half float texture with the images one under the other. */
    private static final class Terms implements AutoCloseable {
        final GpuTexture texture;
        final GpuTextureView view;

        private Terms(GpuTexture texture, GpuTextureView view) {
            this.texture = texture;
            this.view = view;
        }

        /** Sends the term images of a sky to a new texture, which has one texel of 0 when the sky has no terms. */
        static Terms upload(SkyImage img, String label) {
            int width = img.termTextureWidth(), height = img.termTextureHeight();
            GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA16_FLOAT, width, height, 1, 1);
            // The write copies the bytes at once, so they can go free after it.
            ByteBuffer bytes = MemoryUtil.memCalloc(width * height * 8);
            try {
                bytes.asShortBuffer().put(img.terms());
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, bytes, 0, 0, 0, 0, width, height);
            } finally {
                MemoryUtil.memFree(bytes);
            }
            return new Terms(texture, RenderSystem.getDevice().createTextureView(texture));
        }

        /** Binds the texture to the sampler of the sky shader, with linear filters, and with wrap only around the sky. */
        void bind(RenderPass pass) {
            pass.setUniform("AlbedoTerms", view, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false));
        }

        @Override
        public void close() {
            view.close();
            texture.close();
        }
    }

    /** The uniform block of the sky shader in the layout of {@link SkyProgram}, and its term images. */
    private static final BindGroupLayout SKY_BLOCK = BindGroupLayout.builder()
            .withUniform("AlbedoSky", UniformType.UNIFORM_BUFFER)
            .withUniform("AlbedoTerms", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // The sky on the sphere with no blend, no depth test or write and no cull: the sky is the first thing in the frame.
    private static final Pipeline SHARP = new Pipeline(RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(SKY_BLOCK)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_sharp"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_sharp"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_sharp"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Util.backgroundExecutor());

    // The sky into the equirectangular image of the CPU bake, for the harness, with no depth texture.
    private static final Pipeline EQUIRECT = new Pipeline(RenderPipeline.builder()
            .withBindGroupLayout(SKY_BLOCK)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_equirect"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_equirect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_equirect"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Util.backgroundExecutor());

    private static GpuBuffer block;
    private static Terms terms;
    private static long blockSerial = -1;
    /** True when the last {@link #prepare} sent the image of this frame and the pipeline is ready, so that the sky pass draws sharp. */
    private static boolean ready;

    private SharpSky() {}

    /** Starts the compile and sends the numbers and the term images of a new image to the GPU, before the sky pass opens. */
    static void prepare(SkyImage img) {
        ready = ModernSky.wantsSharp(img) && SHARP.get() != null;
        if (!ready || blockSerial == img.serial()) return;
        if (block != null) block.close();
        if (terms != null) terms.close();
        block = upload(img.program(), "Albedo sky block");
        terms = Terms.upload(img, "Albedo sky terms");
        blockSerial = img.serial();
    }

    /**
     * Draws the image of the last {@link #prepare} sharp in the sky pass, also when a newer image came after it, and returns
     * true, or returns false when the baked texture must draw it.
     */
    static boolean draw(RenderPass pass, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        if (!ready) return false;
        pass.pushDebugGroup(() -> "Albedo sharp sky");
        pass.setPipeline(SHARP.compiled);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("AlbedoSky", block);
        terms.bind(pass);
        pass.setVertexBuffer(0, sphere.slice());
        pass.draw(vertexCount, 1, 0, 0);
        pass.popDebugGroup();
        return true;
    }

    /** A uniform buffer with the block of a program. */
    private static GpuBuffer upload(SkyProgram program, String label) {
        float[] values = program.block();
        ByteBuffer bytes = MemoryUtil.memAlloc(values.length * 4);
        try {
            bytes.asFloatBuffer().put(values);
            return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    /**
     * Draws the program and the term images of a sky {@code repeat} times into an equirectangular image of this size, then
     * gives the pixels and the GPU time of one draw, and returns false while the shader compiles or when it cannot compile.
     *
     * @param png a file for the image, or null
     */
    static boolean drawEquirect(SkyImage sky, int width, int height, int repeat, File png, Harness.SkyDone done) {
        CompiledRenderPipeline pipeline = EQUIRECT.get();
        if (pipeline == null) return false;
        TextureTarget target = new TextureTarget("Albedo sky compare", width, height, GpuFormat.RGBA8_UNORM, null);
        GpuBuffer uniforms = upload(sky.program(), "Albedo sky compare block");
        Terms skyTerms = Terms.upload(sky, "Albedo sky compare terms");
        GpuBuffer quad = quad();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuQueryPool timer = RenderSystem.getDevice().createTimestampQueryPool(2);
        try {
            encoder.writeTimestamp(timer, 0);
            for (int i = 0; i < repeat; i++) {
                try (RenderPass pass = encoder.createRenderPass(() -> "Albedo sky compare", target.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setUniform("AlbedoSky", uniforms);
                    skyTerms.bind(pass);
                    pass.setVertexBuffer(0, quad.slice());
                    pass.draw(6, 1, 0, 0);
                }
            }
            encoder.writeTimestamp(timer, 1);
        } catch (RuntimeException e) {
            timer.close();
            target.destroyBuffers();
            uniforms.close();
            skyTerms.close();
            quad.close();
            throw e;
        }
        Screenshot.takeScreenshot(target, image -> {
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) argb[y * width + x] = image.getPixel(x, y);
            }
            if (png != null) {
                try {
                    image.writeToFile(png);
                } catch (IOException e) {
                    Harness.log("! cannot save " + png + ": " + e);
                }
            }
            image.close();
            java.util.OptionalLong[] t = timer.getValues(0, 2);
            long nanos = t[0].isPresent() && t[1].isPresent()
                    ? (long) ((t[1].getAsLong() - t[0].getAsLong()) * RenderSystem.getDevice().getDeviceInfo().timestampPeriod() / repeat) : -1;
            timer.close();
            target.destroyBuffers();
            uniforms.close();
            skyTerms.close();
            quad.close();
            done.accept(argb, nanos);
        });
        return true;
    }

    /** Two triangles over the whole image, with texture coordinates from the top left corner. */
    private static GpuBuffer quad() {
        float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder bytes = com.mojang.blaze3d.vertex.ByteBufferBuilder.exactlySized(
                corners.length * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (float[] c : corners) b.addVertex(0, 0, 0).setUv(c[0], c[1]);
            try (com.mojang.blaze3d.vertex.MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Albedo sky compare quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }

    /**
     * The shaders of one pipeline, read on the render thread from the jar of the mod or from the resources of the game, with
     * the text of each include in them: shaderc calls back into Java for an include line, and on Windows the compiled code of
     * that call can write below the guard page of a worker stack that the compiler made deep, which stops the JVM.
     */
    private static final class Source implements ShaderSource {
        private final Map<ShaderType, String> shaders = new EnumMap<ShaderType, String>(ShaderType.class);

        Source(RenderPipeline pipeline) throws IOException {
            for (Map.Entry<ShaderType, Identifier> e : pipeline.getShaders().entrySet()) {
                Identifier file = e.getKey().idConverter().idToFile(e.getValue());
                String text = file.getNamespace().equals(Albedo.MOD_ID) ? SkyShaderSource.read(file.getPath().substring("shaders/".length())) : resource(file);
                shaders.put(e.getKey(), SkyShaderSource.inline(text, Source::include));
            }
        }

        private static String include(String id) throws IOException {
            Identifier parsed = Identifier.parse(id);
            if (parsed.getNamespace().equals(Albedo.MOD_ID)) return SkyShaderSource.readInclude(id);
            return resource(parsed.withPrefix("shaders/include/"));
        }

        private static String resource(Identifier file) throws IOException {
            return Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).readAllAsString();
        }

        @Override
        public String getShader(Identifier id, ShaderType type) {
            return shaders.get(type);
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            return null;
        }

        @Override
        public void close() {}
    }
}
//?}
