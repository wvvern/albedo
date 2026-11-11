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
        // Opaque white from 26.1, because the fluid renderer there uses the alpha of the tint and older versions ignore it.
        //? if >=26.1 {
        if (ModernHooks.removeFluidTint()) cir.setReturnValue(0xFFFFFFFF);
        //?} else {
        /*if (ModernHooks.removeFluidTint()) cir.setReturnValue(0xFFFFFF);
        *///?}
    }
}
