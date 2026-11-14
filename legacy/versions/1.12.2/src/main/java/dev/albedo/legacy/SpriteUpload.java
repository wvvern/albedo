package dev.albedo.legacy;

import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.data.AnimationMetadataSection;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts new pixels into a sprite of the block atlas: the strip goes into frames as loadSpriteFrames puts it, then the
 * same generateMipmaps call as the atlas load makes the mip levels.
 */
public final class SpriteUpload implements TextureStore.Upload {
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
        TextureMap atlas = SpriteFrames.atlas(sprite);
        if (atlas == null) return false; // a reload replaced it
        int mipLevels = SpriteFrames.mipLevels(sprite);
        int[] strip = PixelBuffer.Heap.copyOf(pixels).argb();
        sprite.setFramesTextureData(frames(strip, sprite.getIconWidth() * sprite.getIconHeight(), mipLevels, ((SpriteAnimation) sprite).albedo$animation()));
        sprite.generateMipmaps(mipLevels);
        SpriteFrames.uploadCurrentFrame(atlas, sprite);
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
}
