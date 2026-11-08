package dev.worldpainter.legacy.mixin;

import dev.worldpainter.legacy.LegacyHooks;
import net.minecraft.world.ColorizerGrass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Grass colormap; renderer-independent, so it also covers Forge's lighting pipeline and OptiFine. */
@Mixin(ColorizerGrass.class)
public abstract class ColorizerGrassMixin {
    @Inject(method = "setGrassBiomeColorizer", at = @At("HEAD"))
    private static void worldpainter$flatten(int[] pixels, CallbackInfo ci) {
        LegacyHooks.colormap("grass", pixels);
    }
}
