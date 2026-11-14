package dev.albedo.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;

/** The atlas and frame steps that the SpriteUpload of each version shares. */
final class SpriteFrames {
    private SpriteFrames() {}

    /** The block atlas, after the Chroma pass puts back its painted colors, or null when a reload replaced the sprite. */
    static TextureMap atlas(TextureAtlasSprite sprite) {
        // The new sprite goes into the colors that Albedo painted, which the Chroma pass copies at the start of the next frame.
        LegacyChroma.restore();
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        return atlas == null || atlas.getAtlasSprite(sprite.getIconName()) != sprite ? null : atlas;
    }

    /** Binds the atlas on the default unit and uploads the frame that the sprite shows now. */
    static void uploadCurrentFrame(TextureMap atlas, TextureAtlasSprite sprite) {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(atlas.getGlTextureId());
        TextureUtil.uploadTextureMipmap(currentFrame(sprite), sprite.getIconWidth(), sprite.getIconHeight(),
                sprite.getOriginX(), sprite.getOriginY(), false, false);
    }

    /** The mip levels of the atlas when the sprite was made: the atlas can use fewer than the video setting. */
    static int mipLevels(TextureAtlasSprite sprite) {
        for (int i = 0; i < sprite.getFrameCount(); i++) {
            int[][] frame = sprite.getFrameTextureData(i);
            if (frame != null) return frame.length - 1;
        }
        throw new IllegalStateException("sprite " + sprite.getIconName() + " has no frames");
    }

    /** The frame that the atlas shows now, where the clock and the compass keep the frame index in the frame counter. */
    static int[][] currentFrame(TextureAtlasSprite sprite) {
        SpriteAnimation a = (SpriteAnimation) sprite;
        AnimationMetadataSection animation = a.albedo$animation();
        int index = animation != null ? animation.getFrameIndex(a.albedo$frameCounter()) : a.albedo$frameCounter();
        if (index < 0 || index >= sprite.getFrameCount()) index = 0;
        return sprite.getFrameTextureData(index);
    }
}
