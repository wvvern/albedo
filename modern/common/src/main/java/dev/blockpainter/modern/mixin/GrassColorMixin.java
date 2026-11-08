package dev.blockpainter.modern.mixin;

import dev.blockpainter.modern.ModernHooks;
import net.minecraft.world.level.GrassColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Grass colormap, flattened as it is (re)loaded. Same {@code init(int[])} shape since 1.13. */
@Mixin(GrassColor.class)
public abstract class GrassColorMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private static void blockpainter$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.flattenColormap(pixels);
    }
}
