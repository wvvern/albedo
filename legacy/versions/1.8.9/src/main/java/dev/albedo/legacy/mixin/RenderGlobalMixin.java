package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import dev.albedo.legacy.LegacySky;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skip the sky (overworld and End) for a flat sky color, and draw the sharp custom sky after the solid world; MixinPlugin
 * also sends the sky draws of this target through LegacySky with LegacySkyPatch, with and without OptiFine.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void albedo$flatSky(float partialTicks, int pass, CallbackInfo ci) {
        if (LegacyHooks.overridesSky()) ci.cancel();
    }

    // The cutout layer is the last solid layer of the world.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"), require = 0)
    private void albedo$skyAfterSolid(EnumWorldBlockLayer layer, double partialTicks, int pass, Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (layer != EnumWorldBlockLayer.CUTOUT) return;
        LegacyHooks.AFTER_SOLID.run();
        LegacySky.drawWaiting();
    }

    // Below the cloud height the clouds draw before the world and blend with the sky, so the sky cannot wait longer.
    @Inject(method = "renderClouds(FI)V", at = @At("HEAD"), require = 0)
    private void albedo$skyBeforeClouds(float partialTicks, int pass, CallbackInfo ci) {
        LegacyHooks.BEFORE_CLOUDS.run();
        LegacySky.drawWaiting();
    }
}
