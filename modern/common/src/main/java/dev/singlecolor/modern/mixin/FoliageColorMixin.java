package dev.singlecolor.modern.mixin;

import dev.singlecolor.modern.ModernHooks;
import net.minecraft.world.level.FoliageColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Foliage colormap, flattened as it is (re)loaded. */
@Mixin(FoliageColor.class)
public abstract class FoliageColorMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private static void singlecolor$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.flattenColormap(pixels);
    }
}
