package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fog color is also the clear color behind the world; make both the flat sky color. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow private float fogColorRed;
    @Shadow private float fogColorGreen;
    @Shadow private float fogColorBlue;

    @Inject(method = "updateFogColor", at = @At("TAIL"))
    private void albedo$flatFog(float partialTicks, CallbackInfo ci) {
        float[] rgb = LegacyHooks.skyRgb();
        if (rgb == null) return;
        fogColorRed = rgb[0];
        fogColorGreen = rgb[1];
        fogColorBlue = rgb[2];
        GlStateManager.clearColor(fogColorRed, fogColorGreen, fogColorBlue, 0.0F);
    }
}
