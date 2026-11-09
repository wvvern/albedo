package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacySky;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Draws the custom sky in place of the overworld sky disc, and leaves out the stars when the sky hides them, the dark
 * lower discs, the sunrise fan and the box below the horizon, for both the VBO and the display list path, by the
 * ordinals of vanilla renderSky (OptiFine changes this method, so MixinPlugin turns this mixin off with OptiFine).
 */
@Mixin(RenderGlobal.class)
public abstract class LegacySkyVanillaMixin {
    private static final String DRAW_ARRAYS = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V";
    private static final String CALL_LIST = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V";
    private static final String TESSELLATOR = "Lnet/minecraft/client/renderer/Tessellator;draw()V";

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = DRAW_ARRAYS, ordinal = 0))
    private void albedo$skyVbo(VertexBuffer buffer, int mode) {
        if (!LegacySky.draw()) buffer.drawArrays(mode);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = DRAW_ARRAYS, ordinal = 1))
    private void albedo$starsVbo(VertexBuffer buffer, int mode) {
        if (!LegacySky.hidesStars()) buffer.drawArrays(mode);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = DRAW_ARRAYS, ordinal = 2))
    private void albedo$darkVbo(VertexBuffer buffer, int mode) {
        if (!LegacySky.active()) buffer.drawArrays(mode);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = CALL_LIST, ordinal = 0))
    private void albedo$skyList(int list) {
        if (!LegacySky.draw()) GlStateManager.callList(list);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = CALL_LIST, ordinal = 1))
    private void albedo$starsList(int list) {
        if (!LegacySky.hidesStars()) GlStateManager.callList(list);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = CALL_LIST, ordinal = 2))
    private void albedo$darkList(int list) {
        if (!LegacySky.active()) GlStateManager.callList(list);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = CALL_LIST, ordinal = 3))
    private void albedo$lowerList(int list) {
        if (!LegacySky.active()) GlStateManager.callList(list);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = TESSELLATOR, ordinal = 0))
    private void albedo$sunrise(Tessellator tessellator) {
        if (LegacySky.active()) LegacySky.discard(tessellator);
        else tessellator.draw();
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = TESSELLATOR, ordinal = 3))
    private void albedo$belowHorizon(Tessellator tessellator) {
        if (LegacySky.active()) LegacySky.discard(tessellator);
        else tessellator.draw();
    }
}
