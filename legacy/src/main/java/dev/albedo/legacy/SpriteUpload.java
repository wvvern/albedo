package dev.albedo.legacy;

import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
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
    /** The animation state of a sprite, which TextureAtlasSpriteMixin implements. */
    public interface Animation {
        int albedo$frameCounter();

        int albedo$tickCounter();

        void albedo$setCounters(int frameCounter, int tickCounter);

        AnimationMetadataSection albedo$animation();
    }

    private final TextureAtlasSprite sprite;
    /**
     * The images that loadSprite got, where index 0 is null (the repaint gives it) and other levels are mip images
     * from a pack.
     */
    private final BufferedImage[] images;
    private final AnimationMetadataSection meta;

    SpriteUpload(TextureAtlasSprite sprite, BufferedImage[] images, AnimationMetadataSection meta) {
        this.sprite = sprite;
        this.images = images.clone();
        this.images[0] = null;
        this.meta = meta;
    }

    /** A still sprite writes one frame into the atlas, but an animated sprite loads all its frames again, so its Chroma colors stay still. */
    @Override
    public boolean inPlace() {
        return meta == null;
    }

    @Override
    public boolean upload(PixelBuffer pixels) throws Exception {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        if (atlas == null || atlas.getAtlasSprite(sprite.getIconName()) != sprite) return false; // a reload replaced it
        if (OptiFine.shaders()) throw new IllegalStateException("OptiFine shaders use their own atlas upload");
        int mipLevels = mipLevels(sprite);
        TextureAtlasSprite single = OptiFine.spriteSingle(sprite);
        int[] counters = counters(sprite);
        int[] singleCounters = single == null ? null : counters(single);

        BufferedImage[] next = images.clone();
        next[0] = BufferedImagePixels.toImage(pixels);
        sprite.loadSprite(next, meta); // with OptiFine, this also loads the single copy
        sprite.generateMipmaps(mipLevels);
        restore(sprite, counters);
        if (single != null) restore(single, singleCounters);

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(atlas.getGlTextureId());
        TextureUtil.uploadTextureMipmap(currentFrame(sprite), sprite.getIconWidth(), sprite.getIconHeight(),
                sprite.getOriginX(), sprite.getOriginY(), false, false);
        if (single != null && OptiFine.bindSpriteTexture(sprite)) {
            TextureUtil.uploadTextureMipmap(currentFrame(single), single.getIconWidth(), single.getIconHeight(),
                    single.getOriginX(), single.getOriginY(), false, true);
            GlStateManager.bindTexture(atlas.getGlTextureId());
        }
        return true;
    }

    /** The mip levels of the atlas when the sprite was made: the atlas can use fewer than the video setting. */
    private static int mipLevels(TextureAtlasSprite sprite) {
        for (int i = 0; i < sprite.getFrameCount(); i++) {
            int[][] frame = sprite.getFrameTextureData(i);
            if (frame != null) return frame.length - 1;
        }
        throw new IllegalStateException("sprite " + sprite.getIconName() + " has no frames");
    }

    private static int[] counters(TextureAtlasSprite sprite) {
        Animation a = (Animation) sprite;
        return new int[]{a.albedo$frameCounter(), a.albedo$tickCounter()};
    }

    private static void restore(TextureAtlasSprite sprite, int[] counters) {
        ((Animation) sprite).albedo$setCounters(counters[0], counters[1]);
    }

    /** The frame that the atlas shows now, where the clock and the compass keep the frame index in the frame counter. */
    private static int[][] currentFrame(TextureAtlasSprite sprite) {
        Animation a = (Animation) sprite;
        AnimationMetadataSection animation = a.albedo$animation();
        int index = animation != null ? animation.getFrameIndex(a.albedo$frameCounter()) : a.albedo$frameCounter();
        if (index < 0 || index >= sprite.getFrameCount()) index = 0;
        return sprite.getFrameTextureData(index);
    }
}
