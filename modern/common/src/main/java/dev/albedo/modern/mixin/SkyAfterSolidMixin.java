package dev.albedo.modern.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the custom sky that the solid world is drawn, so that the sky shader draws only the pixels that the world left
 * empty; if a renderer mod removes this point, the sky draws in the sky pass.
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class SkyAfterSolidMixin {
    @Inject(method = "executeSolid", at = @At("TAIL"), require = 0)
    private void albedo$skyAfterSolid(net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
                                      net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame frame,
                                      com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        dev.albedo.modern.PearlSky.afterSolid(pass);
    }
}
//?} elif >=1.21.11 {
/*@Mixin(com.mojang.blaze3d.platform.Lighting.class)
public abstract class SkyAfterSolidMixin {
    // The level renderer sets the light of the level directly after the solid terrain, also when Sodium draws the terrain.
    @Inject(method = "setupFor", at = @At("HEAD"), require = 0)
    private void albedo$skyAfterSolid(com.mojang.blaze3d.platform.Lighting.Entry entry, CallbackInfo ci) {
        if (entry == com.mojang.blaze3d.platform.Lighting.Entry.LEVEL) dev.albedo.modern.PrecompiledSky.afterSolid();
    }
}
*///?} else {
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class SkyAfterSolidMixin {
    // The cutout layer is the last solid layer of the world.
    @Inject(method = "renderSectionLayer", at = @At("TAIL"), require = 0)
    private void albedo$skyAfterSolid(net.minecraft.client.renderer.RenderType type, double x, double y, double z,
                                      org.joml.Matrix4f modelView, org.joml.Matrix4f projection, CallbackInfo ci) {
        if (type == net.minecraft.client.renderer.RenderType.cutout()) dev.albedo.modern.GlSky.afterSolid();
    }
}
*///?}
