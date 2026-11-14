package dev.albedo.modern;

//? if renderpearl {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.albedo.core.Albedo;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyLayerSteps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A pipeline of the mod that compiles from a {@link Source} on an executor and is ready on a later call, or in the same
 * call when the executor runs the compile at once.
 */
final class PearlPipeline {
    private final RenderPipeline pipeline;
    private final Executor executor;
    private final String steps;
    /** The name of the shader in the log, and what the game does when it cannot compile. */
    private final String name, fallback;
    private ShaderState state = ShaderState.NOT_STARTED;
    /** The compile while RUNNING, else null. */
    private CompletableFuture<CompiledRenderPipeline.Pending> pending;
    private CompiledRenderPipeline compiled;

    /** @param steps the layer steps of the sky include of a sky shader */
    PearlPipeline(RenderPipeline pipeline, Executor executor, String steps) {
        this(pipeline, executor, steps, "sharp sky shader", "The sky draws from the baked image.");
    }

    /** A pipeline with no sky include. */
    PearlPipeline(RenderPipeline pipeline, Executor executor, String name, String fallback) {
        this(pipeline, executor, "", name, fallback);
    }

    private PearlPipeline(RenderPipeline pipeline, Executor executor, String steps, String name, String fallback) {
        this.pipeline = pipeline;
        this.executor = executor;
        this.steps = steps;
        this.name = name;
        this.fallback = fallback;
    }

    /** Frees the compiled pipeline, also one that is still compiling, whose result goes free on the render thread when it comes. */
    void close() {
        if (compiled != null) compiled.close();
        if (state == ShaderState.RUNNING) pending.thenAcceptAsync(PearlPipeline::closeLate, Minecraft.getInstance());
        compiled = null;
        pending = null;
        state = state.to(ShaderState.CLOSED);
    }

    /**
     * Starts or finishes the compile on the render thread and returns the state after it, which is FAILED at once when an
     * executor that runs at once fails the compile.
     */
    ShaderState compile() {
        if (state == ShaderState.NOT_STARTED) start();
        if (state == ShaderState.RUNNING && pending.isDone()) finish();
        return state;
    }

    /** Starts or finishes the compile on the render thread, and returns the compiled pipeline once it is ready, else null. */
    CompiledRenderPipeline get() {
        return compile().usable() ? compiled : null;
    }

    private void start() {
        state = state.to(ShaderState.RUNNING);
        try {
            pending = RenderSystem.getDevice().compilePipeline(pipeline, new Source(pipeline, steps), executor);
        } catch (IOException | RuntimeException e) {
            fail(e);
        }
    }

    private void finish() {
        CompletableFuture<CompiledRenderPipeline.Pending> done = pending;
        pending = null;
        try {
            compiled = done.join().finishCompile();
        } catch (RuntimeException e) {
            fail(e);
            return;
        }
        if (compiled == null) {
            fail(null);
        } else {
            state = state.to(ShaderState.READY);
            Albedo.get().platform().info("Compiled the " + name + " " + pipeline.getLocation());
        }
    }

    private void fail(Throwable e) {
        state = state.to(ShaderState.FAILED);
        compiled = null;
        Albedo.get().platform().warn("Cannot compile the " + name + " " + pipeline.getLocation() + ". " + fallback, e);
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

    /** A vertex buffer of triangles from the numbers x, y, z, u, v of each vertex. */
    static GpuBuffer vertexBuffer(String label, float[] v) {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(v.length / 5 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) b.addVertex(v[i], v[i + 1], v[i + 2]).setUv(v[i + 3], v[i + 4]);
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
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
                // Fabric without Fabric API loads no mod assets, so the shaders of the mod come from its jar.
                String text = id.getNamespace().equals(Albedo.MOD_ID)
                        ? dev.albedo.core.shader.ShaderSource.read(id.getPath() + (e.getKey() == ShaderType.VERTEX ? ".vsh" : ".fsh"))
                        : resource(e.getKey().idConverter().idToFile(id));
                dev.albedo.core.shader.ShaderSource.Includes mod = SkyLayerSteps.includes(steps);
                shaders.put(e.getKey(), dev.albedo.core.shader.ShaderSource.inline(text, include -> include(include, mod)));
            }
        }

        private static String include(String id, dev.albedo.core.shader.ShaderSource.Includes mod) throws IOException {
            Identifier parsed = Identifier.parse(id);
            return parsed.getNamespace().equals(Albedo.MOD_ID) ? mod.read(parsed.toString()) : resource(parsed.withPrefix("shaders/include/"));
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
