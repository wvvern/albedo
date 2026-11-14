package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the sky pass when the sky is flat, so the clear color (the flat fog color) shows behind the world. */
@Mixin(LevelRenderer.class)
public abstract class SkyMixin {
    // NeoForge 1.21.11 adds an addSkyPass with a fourth parameter and calls only that one, so the hook goes into each overload.
    //? if >=1.21.2 {
    @Inject(method = "addSkyPass*", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    *///?}
    private void albedo$flatSky(CallbackInfo ci) {
        if (ModernHooks.overridesSky()) ci.cancel();
    }
}
