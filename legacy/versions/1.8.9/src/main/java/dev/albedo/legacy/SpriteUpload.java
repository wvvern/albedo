package dev.albedo.legacy;

import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;

import java.awt.image.BufferedImage;

/**
 * Puts new pixels into a sprite of the block atlas with the same loadSprite and generateMipmaps calls as the atlas
 * load, so that OptiFine also fixes the color of transparent pixels.
 */
public final class SpriteUpload implements TextureStore.Upload {
    /** The animation state of a sprite that a reload of its frames resets. */
    public interface Animation extends SpriteAnimation {
        int albedo$tickCounter();

        void albedo$setCounters(int frameCounter, int tickCounter);
    }

    private final TextureAtlasSprite sprite;
    /**
     * The images that loadSprite got, where index 0 is null (the repaint gives it) and other levels are mip images
     * from a pack.
     */
    private final BufferedImage[] images;
    private final AnimationMetadataSection meta;

    private SpriteUpload(TextureAtlasSprite sprite, BufferedImage[] images, AnimationMetadataSection meta) {
        this.sprite = sprite;
        this.images = images.clone();
        this.images[0] = null;
        this.meta = meta;
    }

    /** Changes the animation strip {@code images[0]} of a sprite before loadSprite makes frames and mip levels from it. */
    public static void process(TextureAtlasSprite sprite, BufferedImage[] images, AnimationMetadataSection meta) {
        if (images == null || images.length == 0) return;
        images[0] = LegacyHooks.processSprite(sprite.getIconName(), images[0], new SpriteUpload(sprite, images, meta));
    }

    @Override
    public boolean gpuHue() {
        return LegacyChroma.gpuHue();
    }

    @Override
    public boolean upload(PixelBuffer pixels) throws Exception {
        TextureMap atlas = SpriteFrames.atlas(sprite);
        if (atlas == null) return false; // a reload replaced it
        if (OptiFine.shaders()) throw new IllegalStateException("OptiFine shaders use their own atlas upload");
        int mipLevels = SpriteFrames.mipLevels(sprite);
        TextureAtlasSprite single = OptiFine.spriteSingle(sprite);
        int[] counters = counters(sprite);
        int[] singleCounters = single == null ? null : counters(single);

        BufferedImage[] next = images.clone();
        next[0] = BufferedImagePixels.toImage(pixels);
        sprite.loadSprite(next, meta); // with OptiFine, this also loads the single copy
        sprite.generateMipmaps(mipLevels);
        restore(sprite, counters);
        if (single != null) restore(single, singleCounters);

        SpriteFrames.uploadCurrentFrame(atlas, sprite);
        if (single != null && OptiFine.bindSpriteTexture(sprite)) {
            TextureUtil.uploadTextureMipmap(SpriteFrames.currentFrame(single), single.getIconWidth(), single.getIconHeight(),
                    single.getOriginX(), single.getOriginY(), false, true);
            GlStateManager.bindTexture(atlas.getGlTextureId());
        }
        return true;
    }

    private static int[] counters(TextureAtlasSprite sprite) {
        Animation a = (Animation) sprite;
        return new int[]{a.albedo$frameCounter(), a.albedo$tickCounter()};
    }

    private static void restore(TextureAtlasSprite sprite, int[] counters) {
        ((Animation) sprite).albedo$setCounters(counters[0], counters[1]);
    }
}
