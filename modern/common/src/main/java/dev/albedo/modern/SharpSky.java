package dev.albedo.modern;

//? if >=26.3 {
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
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
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.sky.SkyTurn;
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
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Draws the custom sky with the sky shader for each screen pixel, so that it is sharp at each resolution and field of
 * view, when the design, the setting and the GPU permit; else the baked texture draws it.
 */
final class SharpSky {
    private enum Compile { NOT_STARTED, RUNNING, READY, FAILED }

    /**
     * A pipeline that compiles from a {@link Source} on an executor and is ready on a later call, or in the same call when the
     * executor runs the compile at once.
     */
    static final class Pipeline {
        private final RenderPipeline pipeline;
        private final Executor executor;
        private final String steps;
        /** The name of the shader in the log, and what the game does when it cannot compile. */
        private final String name, fallback;
        private Compile state = Compile.NOT_STARTED;
        private CompletableFuture<CompiledRenderPipeline.Pending> pending;
        private CompiledRenderPipeline compiled;

        /** @param steps the layer steps of the sky include of a sky shader */
        Pipeline(RenderPipeline pipeline, Executor executor, String steps) {
            this(pipeline, executor, steps, "sharp sky shader", "The sky draws from the baked image.");
        }

        /** A pipeline with no sky include. */
        Pipeline(RenderPipeline pipeline, Executor executor, String name, String fallback) {
            this(pipeline, executor, "", name, fallback);
        }

        private Pipeline(RenderPipeline pipeline, Executor executor, String steps, String name, String fallback) {
            this.pipeline = pipeline;
            this.executor = executor;
            this.steps = steps;
            this.name = name;
            this.fallback = fallback;
        }

        /** Frees the compiled pipeline, also one that is still compiling, whose result goes free on the render thread when it comes. */
        void close() {
            if (compiled != null) compiled.close();
            if (state == Compile.RUNNING) pending.thenAcceptAsync(SharpSky::closeLate, Minecraft.getInstance());
            compiled = null;
            state = Compile.FAILED;
        }

        /** Whether the compile failed, which it does at once in the call that starts it with an executor that runs at once. */
        boolean failed() {
            return state == Compile.FAILED;
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
                pending = RenderSystem.getDevice().compilePipeline(pipeline, new Source(pipeline, steps), executor);
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
                Albedo.get().platform().info("Compiled the " + name + " " + pipeline.getLocation());
            }
        }

        private void fail(Throwable e) {
            state = Compile.FAILED;
            compiled = null;
            Albedo.get().platform().warn("Cannot compile the " + name + " " + pipeline.getLocation() + ". " + fallback, e);
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

    /** The number of pipelines, which gives each pipeline its own name. */
    private static int pipelines;

    // The sky on the sphere, added to a black frame only where the depth is still the far depth of the clear, with no cull.
    private static final SkyShaderCache<Pipeline> SHARP = new SkyShaderCache<Pipeline>(SkyShaderCache.SIZE, steps -> new Pipeline(RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(SKY_BLOCK)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_sharp_" + pipelines++))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_sharp"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_sharp"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Util.backgroundExecutor(), steps), Pipeline::close);

    // Black on the sphere with no blend where the depth is still the far depth of the clear, which resets only the sky of this
    // level view, compiled on the render thread at its first use.
    private static final Pipeline BLACK = new Pipeline(RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_black"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_sharp"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_black"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "black sky shader", "The sky draws from the baked image.");

    // The sky into the equirectangular image of the CPU bake, for the harness, with no depth texture.
    private static final SkyShaderCache<Pipeline> EQUIRECT = new SkyShaderCache<Pipeline>(SkyShaderCache.SIZE, steps -> new Pipeline(RenderPipeline.builder()
            .withBindGroupLayout(SKY_BLOCK)
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/sky_equirect_" + pipelines++))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_equirect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "core/sky_equirect"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Util.backgroundExecutor(), steps), Pipeline::close);

    /** The bytes of the block of a frame for {@link #writeFrame}, which the write copies at once, so one buffer serves all frames. */
    private static final ByteBuffer FRAME_BYTES = MemoryUtil.memAlloc(SkyProgram.FLOATS * 4);
    private static GpuBuffer block;
    private static Terms terms;
    private static long blockSerial = -1;
    /** The compiled pipeline of the image of the last {@link #prepare}, or null when the baked texture must draw it. */
    private static CompiledRenderPipeline ready;

    private SharpSky() {}

    /**
     * Starts the compile and sends the numbers and the term images of a new image to the GPU, before the sky pass opens,
     * and returns true when the sky shader draws this image.
     */
    static boolean prepare(SkyImage img) {
        ready = ModernSky.wantsSharp(img) ? SHARP.get(img.program().shaderSteps()).get() : null;
        if (ready != null && BLACK.get() == null) ready = null;
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

    /** True when the sky shader draws the image of the last {@link #prepare}. */
    static boolean ready() {
        return ready != null;
    }

    /** Writes the block of a frame, with the time of the layers that move and the turn of the sky, outside a render pass. */
    private static void writeFrame(GpuBuffer target, float[] values) {
        FRAME_BYTES.asFloatBuffer().put(values);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(target.slice(), FRAME_BYTES);
    }

    /** Draws the image of the last {@link #prepare} sharp in a pass that has the depth of the frame, also when a newer image came after it. */
    static void draw(RenderPass pass, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        pass.pushDebugGroup(() -> "Albedo sharp sky");
        pass.setPipeline(ready);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("AlbedoSky", block);
        terms.bind(pass);
        pass.setVertexBuffer(0, sphere.slice());
        pass.draw(vertexCount, 1, 0, 0);
        pass.popDebugGroup();
    }

    /** Draws black where the frame has the far depth, so that the sharp sky can add its color there. */
    static void drawBlack(RenderPass pass, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        pass.pushDebugGroup(() -> "Albedo black sky");
        pass.setPipeline(BLACK.get());
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, sphere.slice());
        pass.draw(vertexCount, 1, 0, 0);
        pass.popDebugGroup();
    }

    /** Frees the result of a compile that ended after its pipeline was closed. */
    private static void closeLate(CompiledRenderPipeline.Pending pending) {
        try {
            CompiledRenderPipeline late = pending.finishCompile();
            if (late != null) late.close();
        } catch (RuntimeException ignored) {
            // The failed compile has nothing to free.
        }
    }

    /** A uniform buffer with a block of a program, which {@link #writeFrame} can change. */
    private static GpuBuffer upload(float[] values, String label) {
        ByteBuffer bytes = MemoryUtil.memAlloc(values.length * 4);
        try {
            bytes.asFloatBuffer().put(values);
            return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    /**
     * Draws the program and the term images of a sky {@code repeat} times into an image of this size, then gives the
     * pixels and the GPU time of one draw, and returns false while the shader compiles or when it cannot compile.
     *
     * @param values the block of the program of the sky for this draw
     * @param view the sky directions of the corners of a camera view, or null for the equirectangular image
     * @param png a file for the image, or null
     */
    static boolean drawEquirect(SkyImage sky, float[] values, int width, int height, int repeat, float[] view, File png, Harness.SkyDone done) {
        CompiledRenderPipeline pipeline = EQUIRECT.get(sky.program().shaderSteps()).get();
        if (pipeline == null) return false;
        TextureTarget target = new TextureTarget("Albedo sky compare", width, height, GpuFormat.RGBA8_UNORM, null);
        GpuBuffer uniforms = upload(values, "Albedo sky compare block");
        Terms skyTerms = Terms.upload(sky, "Albedo sky compare terms");
        GpuBuffer quad = quad(view);
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
            OptionalLong[] t = timer.getValues(0, 2);
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

    /** The {@link SkyImage#quad} of a view, or of the whole image for null. */
    private static GpuBuffer quad(float[] view) {
        float[] v = SkyImage.quad(view);
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(v.length / 5 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (MeshData mesh = b.buildOrThrow()) {
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

        /** @param steps the layer steps that go on top of the sky include */
        Source(RenderPipeline pipeline, String steps) throws IOException {
            for (Map.Entry<ShaderType, Identifier> e : pipeline.getShaders().entrySet()) {
                Identifier id = e.getValue();
                String text = id.getNamespace().equals(Albedo.MOD_ID)
                        ? SkyShaderSource.read(id.getPath() + (e.getKey() == ShaderType.VERTEX ? ".vsh" : ".fsh"))
                        : resource(e.getKey().idConverter().idToFile(id));
                shaders.put(e.getKey(), SkyShaderSource.inline(text, include -> include(include, steps)));
            }
        }

        private static String include(String id, String steps) throws IOException {
            Identifier parsed = Identifier.parse(id);
            return parsed.getNamespace().equals(Albedo.MOD_ID) ? SkyShaderSource.readInclude(parsed.toString(), steps)
                    : resource(parsed.withPrefix("shaders/include/"));
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
