package dev.singlecolor.legacy.mixin;

import dev.singlecolor.legacy.LegacyHooks;
import net.minecraft.world.ColorizerFoliage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Foliage colormap. */
@Mixin(ColorizerFoliage.class)
public abstract class ColorizerFoliageMixin {
    @Inject(method = "setFoliageBiomeColorizer", at = @At("HEAD"))
    private static void singlecolor$flatten(int[] pixels, CallbackInfo ci) {
        LegacyHooks.flattenColormap(pixels);
    }
}
