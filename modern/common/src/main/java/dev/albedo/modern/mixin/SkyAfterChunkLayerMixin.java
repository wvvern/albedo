package dev.albedo.modern.mixin;

//? if >=1.17 && <1.20.5 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.19.3 {
import org.joml.Matrix4f;
//?} else {
/^import com.mojang.math.Matrix4f;
^///?}

// Tells the custom sky that a world layer is drawn on the versions where the layer draw takes a PoseStack, as SkyAfterSolidMixin does.
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class SkyAfterChunkLayerMixin {
    //? if >=1.20.3 {
    @Inject(method = "renderSectionLayer", at = @At("TAIL"), require = 0)
    //?} else {
    /^@Inject(method = "renderChunkLayer", at = @At("TAIL"), require = 0)
    ^///?}
    private void albedo$skyAfterSolid(net.minecraft.client.renderer.RenderType type, com.mojang.blaze3d.vertex.PoseStack pose,
                                      double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        dev.albedo.modern.GlSky.afterLayer(type);
    }
}
*///?}
