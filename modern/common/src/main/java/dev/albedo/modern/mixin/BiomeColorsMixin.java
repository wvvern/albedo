package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.BiomeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Water tint, where vanilla, Fabric API and NeoForge fluid rendering all end up for water. */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    /** Takes no target arguments, so that their types (which moved package in 26.x) do not matter. */
    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void albedo$water(CallbackInfoReturnable<Integer> cir) {
        // Opaque white, because the 26.x fluid renderer uses the alpha of the tint and older versions ignore it.
        if (ModernHooks.removeFluidTint()) cir.setReturnValue(0xFFFFFFFF);
    }
}
