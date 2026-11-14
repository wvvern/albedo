package dev.albedo.modern;

//? if renderpearl {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.texture.ChromaTexels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The Chroma pass on 26.x: a render pass of its own into each mip level of an atlas, with a pipeline that compiles on the
 * render thread from the complete shader text of the mod.
 */
final class ChromaPass implements ChromaBackend {
    /** The turn of each speed, the pristine atlas and the texels, which the fragment shader reads by the texel it draws. */
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("AlbedoChroma", UniformType.UNIFORM_BUFFER)
            .withUniform("AlbedoPristine", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("AlbedoTexels", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // One triangle over the whole mip level with no blend and no depth, which compiles on the render thread, because a
    // compile on a worker thread can stop the JVM on Windows (see SkyShaderSource.inline).
    private static final PearlPipeline PIPELINE = new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(LAYOUT)
            .withLocation(ModIds.id("pipeline/chroma"))
            .withVertexShader(ModIds.id("core/chroma"))
            .withFragmentShader(ModIds.id("core/chroma"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "Chroma shader", "Chroma is off.");

    /** The mip level that the copy pipeline reads. */
    private static final BindGroupLayout COPY_LAYOUT = BindGroupLayout.builder()
            .withUniform("AlbedoSource", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // Copies mip levels above 0; see copy().
    private static final PearlPipeline COPY = new PearlPipeline(RenderPipeline.builder()
            .withBindGroupLayout(COPY_LAYOUT)
            .withLocation(ModIds.id("pipeline/chroma_copy"))
            .withVertexShader(ModIds.id("core/chroma"))
            .withFragmentShader(ModIds.id("core/chroma_copy"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build(), Runnable::run, "Chroma copy shader", "Chroma is off.");

    @Override
    public ShaderState compile() {
        ShaderState chroma = PIPELINE.compile(), copy = COPY.compile();
        if (chroma.usable() && copy.usable()) return ShaderState.READY;
        return chroma.dead() || copy.dead() ? ShaderState.FAILED : ShaderState.RUNNING;
    }

    @Override
    public int[] size(TextureAtlas atlas) {
        GpuTexture texture = texture(atlas);
        return texture == null ? null : new int[]{texture.getWidth(0), texture.getHeight(0), texture.getMipLevels()};
    }

    /** The GPU texture of an atlas, or null before its first upload. */
    private static GpuTexture texture(TextureAtlas atlas) {
        try {
            GpuTexture texture = atlas.getTexture();
            return texture.isClosed() ? null : texture;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @Override
    public ChromaBackend.Target create(TextureAtlas atlas, int[] size) {
        return new Target(atlas.getTexture(), size[2]);
    }

    @Override
    public ChromaBackend.Timer timer() {
        return new Timer();
    }

    private static final class Target implements ChromaBackend.Target {
        private final GpuTexture live;
        private final GpuTexture pristine;
        private final GpuTexture texels;
        private final GpuTextureView[] liveViews;
        private final GpuTextureView[] pristineViews;
        private final GpuTextureView[] texelViews;
        private final GpuBuffer turns;
        private final ByteBuffer turnBytes = MemoryUtil.memAlloc(Chroma.TURN_FLOATS * 4);
        private final int levels;

        Target(GpuTexture live, int levels) {
            this.live = live;
            this.levels = levels;
            int w = live.getWidth(0), h = live.getHeight(0);
            var device = RenderSystem.getDevice();
            pristine = device.createTexture(() -> "Albedo Chroma pristine", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
                    | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, w, h, 1, levels);
            texels = device.createTexture(() -> "Albedo Chroma texels", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, w, h, 1, levels);
            liveViews = new GpuTextureView[levels];
            pristineViews = new GpuTextureView[levels];
            texelViews = new GpuTextureView[levels];
            for (int level = 0; level < levels; level++) {
                liveViews[level] = device.createTextureView(live, level, 1);
                pristineViews[level] = device.createTextureView(pristine, level, 1);
                texelViews[level] = device.createTextureView(texels, level, 1);
            }
            turns = device.createBuffer(() -> "Albedo Chroma turns", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, turnBytes);
        }

        @Override
        public boolean sameTexture(TextureAtlas atlas) {
            return texture(atlas) == live;
        }

        @Override
        public void texels(int[][] texelLevels) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int level = 0; level < levels; level++) {
                int w = texels.getWidth(level), h = texels.getHeight(level);
                ByteBuffer bytes = MemoryUtil.memAlloc(w * h * 4);
                try {
                    ChromaTexels.rgba(texelLevels[level], bytes);
                    encoder.writeToTexture(texels, bytes, level, 0, 0, 0, w, h);
                } finally {
                    MemoryUtil.memFree(bytes);
                }
            }
        }

        @Override
        public void recolor(float[] turnValues) {
            CompiledRenderPipeline pipeline = PIPELINE.get();
            if (pipeline == null) throw new IllegalStateException("the Chroma shader is not compiled");
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            turnBytes.clear();
            turnBytes.asFloatBuffer().put(turnValues);
            encoder.writeToBuffer(turns.slice(), turnBytes);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            for (int level = 0; level < levels; level++) {
                copy(encoder, live, liveViews, pristine, pristineViews, level);
                try (RenderPass pass = encoder.createRenderPass(() -> "Albedo Chroma", liveViews[level], Optional.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setUniform("AlbedoChroma", turns);
                    pass.setUniform("AlbedoPristine", pristineViews[level], nearest);
                    pass.setUniform("AlbedoTexels", texelViews[level], nearest);
                    pass.draw(3, 1, 0, 0);
                }
            }
        }

        @Override
        public void restore() {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int level = 0; level < levels; level++) copy(encoder, pristine, pristineViews, live, liveViews, level);
        }

        /**
         * Copies one mip level into the same level of the other texture, with a draw for each level above 0, because the
         * texture copy of NeoForge 26.3 copies only the level 0.
         */
        private static void copy(CommandEncoder encoder, GpuTexture from, GpuTextureView[] fromViews, GpuTexture to,
                                 GpuTextureView[] toViews, int level) {
            if (level == 0) {
                encoder.copyTextureToTexture(from, to, 0, 0, 0, 0, 0, from.getWidth(0), from.getHeight(0));
                return;
            }
            CompiledRenderPipeline pipeline = COPY.get();
            if (pipeline == null) throw new IllegalStateException("the Chroma copy shader is not compiled");
            try (RenderPass pass = encoder.createRenderPass(() -> "Albedo Chroma copy", toViews[level], Optional.empty())) {
                pass.setPipeline(pipeline);
                pass.setUniform("AlbedoSource", fromViews[level], RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
        }

        @Override
        public long bytes() {
            long out = 0;
            for (int level = 0; level < levels; level++) out += ChromaTexels.levelBytes(pristine.getWidth(level), pristine.getHeight(level));
            return out;
        }

        @Override
        public void close() {
            for (int level = 0; level < levels; level++) {
                liveViews[level].close();
                pristineViews[level].close();
                texelViews[level].close();
            }
            pristine.close();
            texels.close();
            turns.close();
            MemoryUtil.memFree(turnBytes);
        }
    }

    /** Two GPU timestamps around the work of the pass. */
    private static final class Timer implements ChromaBackend.Timer {
        private final GpuQueryPool pool = RenderSystem.getDevice().createTimestampQueryPool(2);

        @Override
        public void begin() {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, 0);
        }

        @Override
        public void end() {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, 1);
        }

        @Override
        public long nanos() {
            OptionalLong[] t = pool.getValues(0, 2);
            if (!t[0].isPresent() || !t[1].isPresent()) return -1;
            return (long) ((t[1].getAsLong() - t[0].getAsLong()) * RenderSystem.getDevice().getDeviceInfo().timestampPeriod());
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
//?}
