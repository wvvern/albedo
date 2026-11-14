package dev.albedo.legacy.mixin;

import dev.albedo.legacy.SpriteAnimation;
import dev.albedo.legacy.SpriteUpload;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Block/item atlas sprites ("minecraft:blocks/stone") as the whole animation strip, before loadSpriteFrames splits it
 * into frames, with the animation state kept so that a repaint (see SpriteUpload) shows the current frame.
 */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin implements SpriteAnimation {
    @Shadow
    protected int frameCounter;
    @Shadow
    private AnimationMetadataSection animationMetadata;

    @Redirect(method = "loadSpriteFrames", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;readBufferedImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage albedo$flatten(InputStream in) throws IOException {
        return SpriteUpload.process((TextureAtlasSprite) (Object) this, TextureUtil.readBufferedImage(in));
    }

    @Override
    public int albedo$frameCounter() {
        return frameCounter;
    }

    @Override
    public AnimationMetadataSection albedo$animation() {
        return animationMetadata;
    }
}
