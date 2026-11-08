package dev.blockpainter.legacy.mixin;

import dev.blockpainter.legacy.LegacyHooks;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Water tint (BlockLiquid asks this for its color). */
@Mixin(BiomeColorHelper.class)
public abstract class BiomeColorHelperMixin {
    @Inject(method = "getWaterColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void blockpainter$water(CallbackInfoReturnable<Integer> cir) {
        if (LegacyHooks.removeFluidTint()) cir.setReturnValue(0xFFFFFF);
    }
}
