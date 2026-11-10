package dev.albedo.modern.mixin;

//? if <1.20.5 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tells the custom sky that the solid world is drawn on the versions where the layer draw takes a PoseStack, as SkyAfterSolidMixin does.
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class SkyAfterChunkLayerMixin {
    // The cutout layer is the last solid layer of the world.
    //? if >=1.20.3 {
    @Inject(method = "renderSectionLayer", at = @At("TAIL"), require = 0)
    //?} else {
    /^@Inject(method = "renderChunkLayer", at = @At("TAIL"), require = 0)
    ^///?}
    private void albedo$skyAfterSolid(net.minecraft.client.renderer.RenderType type, com.mojang.blaze3d.vertex.PoseStack pose,
                                      double x, double y, double z, org.joml.Matrix4f projection, CallbackInfo ci) {
        if (type != net.minecraft.client.renderer.RenderType.cutout()) return;
        ModernHooks.AFTER_SOLID.run();
        dev.albedo.modern.GlSky.afterSolid();
    }
}
*///?}
