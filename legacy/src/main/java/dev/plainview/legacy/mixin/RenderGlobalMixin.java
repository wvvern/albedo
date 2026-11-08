package dev.plainview.legacy.mixin;

import dev.plainview.legacy.LegacyHooks;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip the sky (overworld and End); the clear color set in EntityRendererMixin shows instead. */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void plainview$flatSky(float partialTicks, int pass, CallbackInfo ci) {
        if (LegacyHooks.overridesSky()) ci.cancel();
    }
}
