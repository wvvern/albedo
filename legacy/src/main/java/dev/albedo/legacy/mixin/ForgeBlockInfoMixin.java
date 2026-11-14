package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraftforge.client.model.pipeline.BlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge's block lighting pipeline (on by default) asks this for tints instead of BlockModelRenderer. */
@Mixin(value = BlockInfo.class, remap = false)
public abstract class ForgeBlockInfoMixin {
    @Inject(method = "getColorMultiplier", at = @At("RETURN"), cancellable = true)
    private void albedo$tint(int tint, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(LegacyHooks.tintHook(cir.getReturnValue()));
    }
}
