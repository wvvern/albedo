package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Textures that the game releases, such as downloaded skins, and before 1.21.4 the names of downloaded skins. */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
    //? if >=1.21.11 {
    @Inject(method = "release", at = @At("HEAD"))
    private void albedo$release(net.minecraft.resources.Identifier id, CallbackInfo ci) {
        ModernHooks.released(id);
    }
    //?} else {
    /*@Inject(method = "release", at = @At("HEAD"))
    private void albedo$release(net.minecraft.resources.ResourceLocation id, CallbackInfo ci) {
        ModernHooks.released(id);
    }
    *///?}

    //? if <1.21.4 {
    /*@Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
            at = @At("HEAD"))
    private void albedo$register(net.minecraft.resources.ResourceLocation id, net.minecraft.client.renderer.texture.AbstractTexture texture,
                                 CallbackInfo ci) {
        ModernHooks.registering(id, texture);
    }
    *///?}
}
