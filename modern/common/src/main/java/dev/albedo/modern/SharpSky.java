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
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Draws the custom sky with the sky shader for each screen pixel, so that it is sharp at each resolution and field of
 * view, when the design, the setting and the GPU permit; else the baked texture draws it.
 */
final class SharpSky {
    /** The state of the background compile of one pipeline. */
    private enum Compile { NOT_STARTED, RUNNING, READY, FAILED }

    /** A pipeline that compiles from the shaders of the mod in the background, and is ready on a later frame. */
    private static final class Pipeline {
        final RenderPipeline pipeline;
        Compile state = Compile.NOT_STARTED;
        CompletableFuture<CompiledRenderPipeline.Pending> pending;
        CompiledRenderPipeline compiled;

        Pipeline(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        /** Starts or finishes the compile on the render thread, and returns the compiled pipeline once it is ready, else null. */
        CompiledRenderPipeline get() {
            switch (state) {
                case NOT_STARTED:
                    start();
                    return null;
                case RUNNING:
                    if (pending.isDone()) finish();
                    return compiled;
                case READY:
                    return compiled;
                default:
                    return null;
            }
        }

        private void start() {
            state = Compile.RUNNING;
            try {
                Source source = new Source();
                pending = RenderSystem.getDevice().compilePipeline(pipeline, source, Util.backgroundExecutor());
                pending.whenComplete((done, error) -> source.close());
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
                Albedo.get().platform().info("Compiled the sharp sky shader " + pipeline.getLocation());
            }
        }

        private void fail(Throwable e) {
            state = Compile.FAILED;
            compiled = null;
            Albedo.get().platform().warn("Cannot compile the sharp sky shader " + pipeline.getLocation() + ". The sky draws from the baked image.", e);
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
            int count = img.program().terms();
            int width = count == 0 ? 1 : img.termWidth(), height = count == 0 ? 1 : img.termHeight() * count;
            GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA16_FLOAT, width, height, 1, 1);
            ByteBuffer bytes = ByteBuffer.allocateDirect(width * height * 8).order(ByteOrder.nativeOrder());
            if (count > 0) bytes.asShortBuffer().put(img.terms());
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, bytes, 0, 0, 0, 0, width, height);
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
            .build());

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
            .build());

    private static GpuBuffer block;
    private static Terms terms;
    private static long blockSerial = -1;
    private static Method irisInUse;
    private static Object iris;
    private static boolean irisLooked;

    private SharpSky() {}

    /** True when the image must draw sharp in this frame, before the compile and the upload. */
    private static boolean wanted(SkyImage img) {
        return img.design().render() == SkyDesign.Render.SHARP && img.program().drawsSharp() && !shaderPackInUse();
    }

    /** Starts the compile and sends the numbers and the term images of a new image to the GPU, before the sky pass opens. */
    static void prepare(SkyImage img) {
        if (!wanted(img) || SHARP.get() == null || blockSerial == img.serial()) return;
        if (block != null) block.close();
        if (terms != null) terms.close();
        block = upload(img.program(), "Albedo sky block");
        terms = Terms.upload(img, "Albedo sky terms");
        blockSerial = img.serial();
    }

    /** Draws the image sharp in the sky pass and returns true, or returns false when the baked texture must draw it. */
    static boolean draw(RenderPass pass, SkyImage img, GpuBufferSlice transforms, GpuBuffer sphere, int vertexCount) {
        if (!wanted(img) || SHARP.compiled == null || SHARP.state != Compile.READY || blockSerial != img.serial()) return false;
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

    /** Iris with a shader pack draws the sky in its own way, so the sky then draws from the baked texture. */
    private static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                iris = null;
            }
        }
        if (iris == null) return false;
        try {
            return (Boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** A uniform buffer with the block of a program. */
    private static GpuBuffer upload(SkyProgram program, String label) {
        float[] values = program.block();
        ByteBuffer bytes = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder());
        for (float v : values) bytes.putFloat(v);
        bytes.flip();
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM, bytes);
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

    /** The shaders of the mod from its jar, and the includes of the game with the include of the mod. */
    private static final class Source implements ShaderSource {
        private final Map<Identifier, CachedIncludeSource> includes;

        Source() throws IOException {
            includes = new HashMap<Identifier, CachedIncludeSource>(ShaderManager.listAllIncludes(Minecraft.getInstance().getResourceManager()));
            Identifier own = Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "sky_layers.glsl");
            CachedIncludeSource old = includes.put(own, CachedIncludeSource.create(own, read("include/sky_layers.glsl")));
            if (old != null) old.close();
        }

        @Override
        public String getShader(Identifier id, ShaderType type) {
            if (!id.getNamespace().equals(Albedo.MOD_ID)) return null;
            try {
                return read(id.getPath() + (type == ShaderType.VERTEX ? ".vsh" : ".fsh"));
            } catch (IOException e) {
                Albedo.get().platform().warn("Cannot read the sky shader " + id, e);
                return null;
            }
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            return includes.get(id);
        }

        @Override
        public void close() {
            for (CachedIncludeSource include : includes.values()) include.close();
        }

        /** A text file from the shader folder of the mod in its jar, because a Fabric game without Fabric API does not load mod assets. */
        private static String read(String path) throws IOException {
            try (InputStream in = SharpSky.class.getResourceAsStream("/assets/" + Albedo.MOD_ID + "/shaders/" + path)) {
                if (in == null) throw new IOException("no file " + path);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
//?}
