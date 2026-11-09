package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip the sky (overworld and End) for a flat sky color, and MixinPlugin also sends the sky draws of this target
 * through LegacySky with LegacySkyPatch, with and without OptiFine.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void albedo$flatSky(float partialTicks, int pass, CallbackInfo ci) {
        if (LegacyHooks.overridesSky()) ci.cancel();
    }
}
