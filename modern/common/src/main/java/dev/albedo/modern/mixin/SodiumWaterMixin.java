package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water tint in Sodium 0.5 (1.20.1 to 1.20.6), Embeddium 0.3 and Rubidium 0.7, which read the biome water color from
 * their own cache and not from BiomeColors, so that BiomeColorsMixin does not run; it uses plain Mixin because Forge has
 * no MixinExtras before 1.20.5, and the game starts without these mods (require = 0).
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.color.DefaultColorProviders$WaterColorProvider", remap = false)
public abstract class SodiumWaterMixin {
    /** Takes no target arguments, so that their types do not matter; opaque white is the color of no tint. */
    @Inject(method = "getColor", require = 0, at = @At("HEAD"), cancellable = true)
    private void albedo$water(CallbackInfoReturnable<Integer> cir) {
        if (ModernHooks.removeSodiumWaterTint()) cir.setReturnValue(0xFFFFFFFF);
    }
}
