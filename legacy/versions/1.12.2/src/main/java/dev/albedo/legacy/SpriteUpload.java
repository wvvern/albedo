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
import java.util.ArrayList;
import java.util.List;

/**
 * Puts new pixels into a sprite of the block atlas: the strip goes into frames as loadSpriteFrames puts it, then the
 * same generateMipmaps call as the atlas load makes the mip levels.
 */
public final class SpriteUpload implements TextureStore.Upload {
    /** The animation state of a sprite, which TextureAtlasSpriteMixin implements. */
    public interface Animation {
        int albedo$frameCounter();

        AnimationMetadataSection albedo$animation();
    }

    private final TextureAtlasSprite sprite;

    private SpriteUpload(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    /** Changes the animation strip of a sprite before loadSpriteFrames makes frames from it. */
    public static BufferedImage process(TextureAtlasSprite sprite, BufferedImage strip) {
        return LegacyHooks.processSprite(sprite.getIconName(), strip, new SpriteUpload(sprite));
    }

    @Override
    public boolean gpuHue() {
        return LegacyChroma.gpuHue();
    }

    @Override
    public boolean upload(PixelBuffer pixels) {
        // The new sprite goes into the colors that Albedo painted, which the Chroma pass copies at the start of the next frame.
        LegacyChroma.restore();
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        if (atlas == null || atlas.getAtlasSprite(sprite.getIconName()) != sprite) return false; // a reload replaced it
        int mipLevels = mipLevels(sprite);
        int[] strip = PixelBuffer.Heap.copyOf(pixels).argb();
        sprite.setFramesTextureData(frames(strip, sprite.getIconWidth() * sprite.getIconHeight(), mipLevels, ((Animation) sprite).albedo$animation()));
        sprite.generateMipmaps(mipLevels);

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(atlas.getGlTextureId());
        TextureUtil.uploadTextureMipmap(currentFrame(sprite), sprite.getIconWidth(), sprite.getIconHeight(),
                sprite.getOriginX(), sprite.getOriginY(), false, false);
        return true;
    }

    /**
     * The frames of a strip at the places that loadSpriteFrames gives them, with an empty place for each index that the
     * animation does not name and a place for each mip level of each frame, as generateMipmaps needs.
     */
    private static List<int[][]> frames(int[] strip, int frameLength, int mipLevels, AnimationMetadataSection animation) {
        List<int[][]> frames = new ArrayList<int[][]>();
        if (animation == null) {
            frames.add(levels(strip, mipLevels));
            return frames;
        }
        for (int index : animation.getFrameIndexSet()) {
            while (frames.size() <= index) frames.add(null);
            int[] frame = new int[frameLength];
            System.arraycopy(strip, index * frameLength, frame, 0, frameLength);
            frames.set(index, levels(frame, mipLevels));
        }
        return frames;
    }

    private static int[][] levels(int[] pixels, int mipLevels) {
        int[][] levels = new int[mipLevels + 1][];
        levels[0] = pixels;
        return levels;
    }

    /** The mip levels of the atlas when the sprite was made: the atlas can use fewer than the video setting. */
    private static int mipLevels(TextureAtlasSprite sprite) {
        for (int i = 0; i < sprite.getFrameCount(); i++) {
            int[][] frame = sprite.getFrameTextureData(i);
            if (frame != null) return frame.length - 1;
        }
        throw new IllegalStateException("sprite " + sprite.getIconName() + " has no frames");
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
