package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the sky pass when the sky is flat, which leaves behind the world the frame's clear color that {@link FogMixin}
 * sets to the flat color.
 */
@Mixin(LevelRenderer.class)
public abstract class SkyMixin {
    //? if >=1.21.2 {
    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    *///?}
    private void albedo$flatSky(CallbackInfo ci) {
        if (ModernHooks.overridesSky()) ci.cancel();
    }
}
