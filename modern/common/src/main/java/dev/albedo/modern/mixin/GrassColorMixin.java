package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.world.level.GrassColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Grass colormap, repainted in {@code init(int[])}, which has the same signature on all modern versions. */
@Mixin(GrassColor.class)
public abstract class GrassColorMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private static void albedo$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.colormap("grass", pixels);
    }
}
