package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shading off with Sodium 0.9 and later (26.x), which copies the smooth lighting option into its level slice and does
 * not use the vanilla renderer, so that AmbientOcclusionMixin does not run.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumAmbientOcclusionMixin {
    /** Missing in earlier Sodium versions and Embeddium (require = 0), which ask Minecraft.useAmbientOcclusion instead. */
    @Inject(method = "useAmbientOcclusion()Z", require = 0, at = @At("HEAD"), cancellable = true)
    private void albedo$ambientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        if (!ModernHooks.shading()) cir.setReturnValue(false);
    }
}
