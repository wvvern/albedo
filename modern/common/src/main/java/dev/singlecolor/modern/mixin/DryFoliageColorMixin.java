package dev.singlecolor.modern.mixin;

import dev.singlecolor.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dry foliage colormap (added in 1.21.5). @Pseudo makes this a no-op on versions without the class. */
@Pseudo
@Mixin(targets = "net.minecraft.world.level.DryFoliageColor")
public abstract class DryFoliageColorMixin {
    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private static void singlecolor$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.flattenColormap(pixels);
    }
}
