package dev.albedo.modern;

//? if renderpearl {
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.albedo.core.Albedo;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HarnessLog;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyMesh;
import dev.albedo.core.sky.SkyProgram;
import dev.albedo.core.sky.SkyShaderCache;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Draws the custom sky with the sky shader for each screen pixel, so that it is sharp at each resolution and field of
 * view, when the design, the setting and the GPU permit; else the baked texture draws it.
 */
final class SharpSky {
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
    private static final SkyShaderCache<PearlPipeline> SHARP = new SkyShaderCache<PearlPipeline>(SkyShaderCache.SIZE, steps -> new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(SKY_BLOCK)
            .withLocation(ModIds.id("pipeline/sky_sharp_" + pipelines++))
            .withVertexShader(ModIds.id("core/sky_sharp"))
            .withFragmentShader(ModIds.id("core/sky_sharp"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Util.backgroundExecutor(), steps), PearlPipeline::close);

    // Black on the sphere with no blend where the depth is still the far depth of the clear, which resets only the sky of this
    // level view, compiled on the render thread at its first use.
    private static final PearlPipeline BLACK = new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(ModIds.id("pipeline/sky_black"))
            .withVertexShader(ModIds.id("core/sky_sharp"))
            .withFragmentShader(ModIds.id("core/sky_black"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "black sky shader", "The sky draws from the baked image.");

    // The sky into the equirectangular image of the CPU bake, for the harness, with no depth texture.
    private static final SkyShaderCache<PearlPipeline> EQUIRECT = new SkyShaderCache<PearlPipeline>(SkyShaderCache.SIZE, steps -> new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(SKY_BLOCK)
            .withLocation(ModIds.id("pipeline/sky_equirect_" + pipelines++))
            .withVertexShader(ModIds.id("core/sky_equirect"))
            .withFragmentShader(ModIds.id("core/sky_equirect"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Util.backgroundExecutor(), steps), PearlPipeline::close);

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
        CompareResources res = new CompareResources(sky, values, width, height, view);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try {
            encoder.writeTimestamp(res.timer, 0);
            for (int i = 0; i < repeat; i++) {
                try (RenderPass pass = encoder.createRenderPass(() -> "Albedo sky compare", res.target.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setUniform("AlbedoSky", res.uniforms);
                    res.terms.bind(pass);
                    pass.setVertexBuffer(0, res.quad.slice());
                    pass.draw(6, 1, 0, 0);
                }
            }
            encoder.writeTimestamp(res.timer, 1);
        } catch (RuntimeException e) {
            res.close();
            throw e;
        }
        Screenshot.takeScreenshot(res.target, image -> {
            int[] argb = new NativeImagePixels(image).getAll();
            if (png != null) {
                try {
                    image.writeToFile(png);
                } catch (IOException e) {
                    HarnessLog.fail("cannot save " + png + ": " + e);
                }
            }
            image.close();
            OptionalLong[] t = res.timer.getValues(0, 2);
            long nanos = t[0].isPresent() && t[1].isPresent()
                    ? (long) ((t[1].getAsLong() - t[0].getAsLong()) * RenderSystem.getDevice().getDeviceInfo().timestampPeriod() / repeat) : -1;
            res.close();
            done.accept(argb, nanos);
        });
        return true;
    }

    /** The GPU objects of one {@link #drawEquirect}, which the error path or the screenshot callback frees. */
    private static final class CompareResources implements AutoCloseable {
        final TextureTarget target;
        final GpuBuffer uniforms;
        final Terms terms;
        final GpuBuffer quad;
        final GpuQueryPool timer;

        /** @param view the sky directions of the corners of a camera view, or null for the equirectangular image */
        CompareResources(SkyImage sky, float[] values, int width, int height, float[] view) {
            target = new TextureTarget("Albedo sky compare", width, height, GpuFormat.RGBA8_UNORM, null);
            uniforms = upload(values, "Albedo sky compare block");
            terms = Terms.upload(sky, "Albedo sky compare terms");
            quad = PearlPipeline.vertexBuffer("Albedo sky compare quad", SkyMesh.quad(view));
            timer = RenderSystem.getDevice().createTimestampQueryPool(2);
        }

        @Override
        public void close() {
            timer.close();
            target.destroyBuffers();
            uniforms.close();
            terms.close();
            quad.close();
        }
    }
}
//?}
