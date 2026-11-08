package dev.blockpainter.modern.mixin;

import dev.blockpainter.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shading off with Sodium 0.9 and later (26.x): Sodium copies the smooth lighting option into its level slice and does
 * not use the vanilla renderer, so AmbientOcclusionMixin does not run. Earlier Sodium versions and Embeddium ask
 * Minecraft.useAmbientOcclusion, which AmbientOcclusionMixin changes; there this method does not exist (require = 0).
 * Optional: @Pseudo, so the game starts without Sodium. The target contains no Minecraft types, so remap = false.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumAmbientOcclusionMixin {
    @Inject(method = "useAmbientOcclusion()Z", require = 0, at = @At("HEAD"), cancellable = true)
    private void blockpainter$ambientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        if (!ModernHooks.shading()) cir.setReturnValue(false);
    }
}
