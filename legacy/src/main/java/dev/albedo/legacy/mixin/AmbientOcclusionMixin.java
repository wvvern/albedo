package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shading off, part 1: no smooth lighting (ambient occlusion). */
@Mixin(Minecraft.class)
public abstract class AmbientOcclusionMixin {
    @Inject(method = "isAmbientOcclusionEnabled", at = @At("HEAD"), cancellable = true)
    private static void albedo$ambientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        if (!LegacyHooks.shading()) cir.setReturnValue(false);
    }
}
