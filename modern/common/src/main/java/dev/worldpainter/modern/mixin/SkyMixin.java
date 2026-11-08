package dev.worldpainter.modern.mixin;

import dev.worldpainter.modern.ModernHooks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the sky pass entirely when the sky is flat. What remains behind the world is the frame's clear color, which
 * {@link FogMixin} sets to the flat color (fog uses the same color, so the horizon blends seamlessly).
 */
@Mixin(LevelRenderer.class)
public abstract class SkyMixin {
    //? if >=1.21.2 {
    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    *///?}
    private void worldpainter$flatSky(CallbackInfo ci) {
        if (ModernHooks.overridesSky()) ci.cancel();
    }
}
