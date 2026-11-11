package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dry foliage colormap, with @Pseudo making this a no-op before 1.21.5, where the class does not exist. */
@Pseudo
@Mixin(targets = "net.minecraft.world.level.DryFoliageColor")
public abstract class DryFoliageColorMixin {
    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private static void albedo$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.DRY_FOLIAGE.run();
        ModernHooks.colormap("dry_foliage", pixels);
    }
}
