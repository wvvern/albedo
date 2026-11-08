package dev.singlecolor.legacy.mixin;

import dev.singlecolor.legacy.LegacyHooks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;

/** Block/item atlas sprites ("minecraft:blocks/stone"), before mipmaps and animation frames are split. */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin {
    @Shadow
    public abstract String getIconName();

    @Inject(method = "loadSprite", at = @At("HEAD"))
    private void singlecolor$flatten(BufferedImage[] images, AnimationMetadataSection meta, CallbackInfo ci) {
        if (images != null && images.length > 0 && images[0] != null) {
            images[0] = LegacyHooks.processImage(getIconName(), images[0]);
        }
    }
}
