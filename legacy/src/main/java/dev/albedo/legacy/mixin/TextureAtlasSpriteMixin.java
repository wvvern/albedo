package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import dev.albedo.legacy.SpriteUpload;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;

/**
 * Block/item atlas sprites ("minecraft:blocks/stone"), before mipmaps and animation frames are split. A repaint calls
 * loadSprite again with new pixels (see SpriteUpload), and keeps the animation state through this mixin.
 */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin implements SpriteUpload.Animation {
    @Shadow
    protected int frameCounter;
    @Shadow
    protected int tickCounter;
    @Shadow
    private AnimationMetadataSection animationMetadata;

    @Inject(method = "loadSprite", at = @At("HEAD"))
    private void albedo$flatten(BufferedImage[] images, AnimationMetadataSection meta, CallbackInfo ci) {
        LegacyHooks.processSprite((TextureAtlasSprite) (Object) this, images, meta);
    }

    @Override
    public int albedo$frameCounter() {
        return frameCounter;
    }

    @Override
    public int albedo$tickCounter() {
        return tickCounter;
    }

    @Override
    public void albedo$setCounters(int frameCounter, int tickCounter) {
        this.frameCounter = frameCounter;
        this.tickCounter = tickCounter;
    }

    @Override
    public AnimationMetadataSection albedo$animation() {
        return animationMetadata;
    }
}
