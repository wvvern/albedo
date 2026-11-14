package dev.albedo.modern;

//? if >=1.21.11 && <26.3 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.texture.ChromaTexels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.OptionalLong;

// The Chroma pass from 1.21.11 to 26.2: a render pass of its own into each mip level of an atlas, with a pipeline that the game
// compiles from the complete shader text of the mod; it never casts a GPU texture, because the NeoForge validation layer
// gives wrappers in place of the textures of the game.
final class PrecompiledChromaPass implements ChromaBackend {
    // One triangle over the whole mip level with no blend and no depth, so that each texel of the pass replaces the atlas texel.
    private static final RenderPipeline PIPELINE = DeviceApi.triangles(DeviceApi.sampler(DeviceApi.sampler(
            DeviceApi.uniformBlock(DeviceApi.colorOnly(RenderPipeline.builder()), "AlbedoChroma"), "AlbedoPristine"), "AlbedoTexels")
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/chroma"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA))
            .withCull(false), null)
            .build();

    // Copies mip levels above 0; see copy().
    private static final RenderPipeline COPY = DeviceApi.triangles(DeviceApi.sampler(DeviceApi.colorOnly(RenderPipeline.builder()), "AlbedoSource")
            .withLocation(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "pipeline/chroma_copy"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Albedo.MOD_ID, SkyShaderSource.CHROMA_COPY))
            .withCull(false), null)
            .build();

    // The state of the compile, which the game does again after each resource reload.
    private enum Compile { NOT_STARTED, READY, FAILED }

    private static Compile state = Compile.NOT_STARTED;

    // The complete text of a shader of the mod, with the Chroma include in it.
    private static String source(Identifier id, ShaderType type) {
        try {
            return SkyShaderSource.classic(id.getPath(), type == ShaderType.VERTEX, DeviceApi.dialect());
        } catch (IOException e) {
            Albedo.get().platform().warn("Cannot read the Chroma shader " + id, e);
            return null;
        }
    }

    @Override
    public Shader compile() {
        if (state == Compile.FAILED) return Shader.FAILED;
        // A resource reload empties the pipeline cache, so the pipelines go to the game in each frame before the pass uses them.
        var device = RenderSystem.getDevice();
        if (device.precompilePipeline(PIPELINE, PrecompiledChromaPass::source).isValid()
                && device.precompilePipeline(COPY, PrecompiledChromaPass::source).isValid()) {
            if (state == Compile.NOT_STARTED) Albedo.get().platform().info("Compiled the Chroma shader " + PIPELINE.getLocation());
            state = Compile.READY;
            return Shader.READY;
        }
        state = Compile.FAILED;
        Albedo.get().platform().warn("Cannot compile the Chroma shader " + PIPELINE.getLocation() + ". Chroma is off.", null);
        return Shader.FAILED;
    }

    @Override
    public int[] size(TextureAtlas atlas) {
        GpuTexture texture = texture(atlas);
        return texture == null ? null : new int[]{texture.getWidth(0), texture.getHeight(0), texture.getMipLevels()};
    }

    // The GPU texture of an atlas, or null before its first upload.
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
            pristine = DeviceApi.texture("Albedo Chroma pristine", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
                    | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, w, h, levels);
            texels = DeviceApi.texture("Albedo Chroma texels", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, w, h, levels);
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
                    DeviceApi.write(encoder, texels, bytes, level, w, h);
                } finally {
                    MemoryUtil.memFree(bytes);
                }
            }
        }

        @Override
        public void recolor(float[] turnValues) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            turnBytes.clear();
            turnBytes.asFloatBuffer().put(turnValues);
            encoder.writeToBuffer(turns.slice(), turnBytes);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            for (int level = 0; level < levels; level++) {
                copy(encoder, live, liveViews, pristine, pristineViews, level);
                try (RenderPass pass = DeviceApi.pass("Albedo Chroma", liveViews[level])) {
                    pass.setPipeline(PIPELINE);
                    pass.setUniform("AlbedoChroma", turns);
                    pass.bindTexture("AlbedoPristine", pristineViews[level], nearest);
                    pass.bindTexture("AlbedoTexels", texelViews[level], nearest);
                    DeviceApi.draw(pass, 3);
                }
            }
        }

        @Override
        public void restore() {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int level = 0; level < levels; level++) copy(encoder, pristine, pristineViews, live, liveViews, level);
        }

        // Copies one mip level into the same level of the other texture, with a draw for each level above 0, because the
        // texture copy of 1.21.11 copies only the level 0.
        private static void copy(CommandEncoder encoder, GpuTexture from, GpuTextureView[] fromViews, GpuTexture to,
                                 GpuTextureView[] toViews, int level) {
            if (level == 0) {
                encoder.copyTextureToTexture(from, to, 0, 0, 0, 0, 0, from.getWidth(0), from.getHeight(0));
                return;
            }
            try (RenderPass pass = DeviceApi.pass("Albedo Chroma copy", toViews[level])) {
                pass.setPipeline(COPY);
                pass.bindTexture("AlbedoSource", fromViews[level], RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                DeviceApi.draw(pass, 3);
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

    // A GPU timer query around the work of the pass, which the game allows only one at a time.
    private static final class Timer implements ChromaBackend.Timer {
        private DeviceApi.Timer query;
        private boolean failed;

        @Override
        public void begin() {
            try {
                query = DeviceApi.Timer.begin();
            } catch (IllegalStateException e) {
                failed = true;
            }
        }

        @Override
        public void end() {
            if (query != null) query.end();
        }

        @Override
        public long nanos() {
            if (failed) return 0;
            OptionalLong t = query == null ? OptionalLong.empty() : query.value();
            return t.isPresent() ? t.getAsLong() : -1;
        }

        @Override
        public void close() {
            if (query != null) query.close();
        }
    }
}
*///?}
