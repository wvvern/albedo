package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import net.minecraft.world.level.FoliageColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoliageColor.class)
public abstract class FoliageColorMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private static void albedo$flatten(int[] pixels, CallbackInfo ci) {
        ModernHooks.colormap("foliage", pixels);
    }
}
