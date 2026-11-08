package dev.worldpainter.modern.mixin;

import dev.worldpainter.modern.ModernHooks;
import net.minecraft.world.level.FoliageColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Foliage colormap, flattened as it is (re)loaded. */
@Mixin(FoliageColor.class)
public abstract class FoliageColorMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private static void worldpainter$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.colormap("foliage", pixels);
    }
}
