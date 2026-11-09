package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The location of each downloaded skin and cape, and the textures that the game deletes. */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
    @Inject(method = "loadTexture", at = @At("HEAD"))
    private void albedo$loadTexture(ResourceLocation location, ITextureObject texture, CallbackInfoReturnable<Boolean> cir) {
        LegacyHooks.loadingTexture(location, texture);
    }

    @Inject(method = "deleteTexture", at = @At("HEAD"))
    private void albedo$deleteTexture(ResourceLocation location, CallbackInfo ci) {
        LegacyHooks.deletingTexture(location);
    }
}
