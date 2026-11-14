package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyChroma;
import dev.albedo.legacy.LegacyHooks;
import dev.albedo.legacy.LegacySky;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fog color is also the clear color behind the world, so both get the flat sky color or the custom sky at the horizon;
 * and the start and the end of each frame, which draws the world and the screens, go to the Chroma pass.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow private float fogColorRed;
    @Shadow private float fogColorGreen;
    @Shadow private float fogColorBlue;

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void albedo$frameStart(float partialTicks, long nanoTime, CallbackInfo ci) {
        LegacyChroma.frameStart();
        LegacySky.frameStart(partialTicks);
    }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void albedo$frameEnd(float partialTicks, long nanoTime, CallbackInfo ci) {
        LegacyChroma.frameEnd();
    }

    @Inject(method = "updateFogColor", at = @At("TAIL"))
    private void albedo$flatFog(float partialTicks, CallbackInfo ci) {
        float[] rgb = LegacyHooks.fogRgb(partialTicks);
        if (rgb == null) return;
        fogColorRed = rgb[0];
        fogColorGreen = rgb[1];
        fogColorBlue = rgb[2];
        GlStateManager.clearColor(fogColorRed, fogColorGreen, fogColorBlue, 0.0F);
    }
}
