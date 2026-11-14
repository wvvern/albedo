package dev.albedo.modern;

//? if device_chroma {
//? if renderpearl {
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
*///?}
import com.mojang.blaze3d.systems.RenderSystem;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.texture.ChromaFrames;
import dev.albedo.core.texture.ChromaTexels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalLong;

/**
 * The atlas texture of the device Chroma pass from 1.21.11 with its pristine copy and its texels, which draws a render pass of
 * its own into each mip level; it never casts a GPU texture, because the NeoForge validation layer gives wrappers in place
 * of the textures of the game.
 */
final class DeviceChromaTarget implements ChromaBackend.Target {
    /** The pipelines of a backend, which set themselves on a pass, because their handle types differ between versions. */
    interface Pipelines {
        /** Sets the pipeline that draws the hue of the texels over the pristine copy. */
        void chroma(RenderPass pass);

        /** Sets the pipeline that copies one mip level. */
        void copy(RenderPass pass);
    }

    private final Pipelines pipelines;
    private final GpuTexture live;
    private final GpuTexture pristine;
    private final GpuTexture texels;
    private final GpuTextureView[] liveViews;
    private final GpuTextureView[] pristineViews;
    private final GpuTextureView[] texelViews;
    private final GpuBuffer turns;
    private final ByteBuffer turnBytes = MemoryUtil.memAlloc(Chroma.TURN_FLOATS * 4);
    private final int levels;

    DeviceChromaTarget(GpuTexture live, int levels, Pipelines pipelines) {
        this.pipelines = pipelines;
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

    /** The GPU texture of an atlas, or null before its first upload. */
    static GpuTexture texture(TextureAtlas atlas) {
        try {
            GpuTexture texture = atlas.getTexture();
            return texture.isClosed() ? null : texture;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** The size of the GPU texture of an atlas, or null before its first upload. */
    static ChromaFrames.Size size(TextureAtlas atlas) {
        GpuTexture texture = texture(atlas);
        return texture == null ? null : new ChromaFrames.Size(texture.getWidth(0), texture.getHeight(0), texture.getMipLevels());
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
        GpuSampler nearest = DeviceApi.nearest();
        for (int level = 0; level < levels; level++) {
            copy(encoder, live, liveViews, pristine, pristineViews, level);
            try (RenderPass pass = DeviceApi.pass("Albedo Chroma", liveViews[level])) {
                pipelines.chroma(pass);
                pass.setUniform("AlbedoChroma", turns);
                DeviceApi.bindTexture(pass, "AlbedoPristine", pristineViews[level], nearest);
                DeviceApi.bindTexture(pass, "AlbedoTexels", texelViews[level], nearest);
                DeviceApi.draw(pass, 3);
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
     * texture copy of 1.21.11 and of NeoForge 26.3 copies only the level 0.
     */
    private void copy(CommandEncoder encoder, GpuTexture from, GpuTextureView[] fromViews, GpuTexture to, GpuTextureView[] toViews, int level) {
        if (level == 0) {
            encoder.copyTextureToTexture(from, to, 0, 0, 0, 0, 0, from.getWidth(0), from.getHeight(0));
            return;
        }
        try (RenderPass pass = DeviceApi.pass("Albedo Chroma copy", toViews[level])) {
            pipelines.copy(pass);
            DeviceApi.bindTexture(pass, "AlbedoSource", fromViews[level], DeviceApi.nearest());
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

    /** A GPU timer around the work of the pass, which the game allows only one at a time on 1.21.11. */
    static final class Timer implements ChromaBackend.Timer {
        /** Null when the GPU timer is in use; the pass calls nanos() only after begin(). */
        private DeviceApi.Timer query;

        @Override
        public void begin() {
            try {
                query = DeviceApi.Timer.begin();
            } catch (IllegalStateException e) {
                query = null;
            }
        }

        @Override
        public void end() {
            if (query != null) query.end();
        }

        @Override
        public long nanos() {
            if (query == null) return UNAVAILABLE;
            OptionalLong t = query.value();
            return t.isPresent() ? t.getAsLong() : -1;
        }

        @Override
        public void close() {
            if (query != null) query.close();
        }
    }
}
//?}
