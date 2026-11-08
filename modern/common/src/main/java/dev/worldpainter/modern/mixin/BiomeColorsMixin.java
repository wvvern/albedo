package dev.worldpainter.modern.mixin;

import dev.worldpainter.modern.ModernHooks;
import net.minecraft.client.renderer.BiomeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water tint. Vanilla, Fabric API and NeoForge fluid rendering all end up here for water, so one hook covers every
 * loader. The handler takes no target arguments, so the argument types (which moved package in 26.x) don't matter.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void worldpainter$water(CallbackInfoReturnable<Integer> cir) {
        if (ModernHooks.removeFluidTint()) cir.setReturnValue(0xFFFFFF);
    }
}
