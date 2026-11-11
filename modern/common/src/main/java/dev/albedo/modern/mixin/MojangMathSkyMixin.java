package dev.albedo.modern.mixin;

//? if <1.19.3 {
/*import dev.albedo.modern.GlSky;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.MojangMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The sky hooks of SkyLayersMixin and SkyAfterChunkLayerMixin on the versions whose matrices are com.mojang.math types.
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class MojangMathSkyMixin {
    // The vertex buffer draws of renderSky are the sky disc, the stars and the dark lower disc, in this order.
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void albedo$sky(com.mojang.blaze3d.vertex.VertexBuffer buffer, com.mojang.math.Matrix4f modelView, com.mojang.math.Matrix4f projection,
                            net.minecraft.client.renderer.ShaderInstance shader) {
        if (!GlSky.draw(MojangMath.columns(modelView), MojangMath.columns(projection))) buffer.drawWithShader(modelView, projection, shader);
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void albedo$stars(com.mojang.blaze3d.vertex.VertexBuffer buffer, com.mojang.math.Matrix4f modelView, com.mojang.math.Matrix4f projection,
                              net.minecraft.client.renderer.ShaderInstance shader) {
        if (!GlSky.hidesStars()) buffer.drawWithShader(modelView, projection, shader);
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void albedo$darkDisc(com.mojang.blaze3d.vertex.VertexBuffer buffer, com.mojang.math.Matrix4f modelView, com.mojang.math.Matrix4f projection,
                                 net.minecraft.client.renderer.ShaderInstance shader) {
        if (!GlSky.replacedDisc()) buffer.drawWithShader(modelView, projection, shader);
    }

    // The cutout layer is the last solid layer of the world.
    @Inject(method = "renderChunkLayer", at = @At("TAIL"), require = 0)
    private void albedo$skyAfterSolid(net.minecraft.client.renderer.RenderType type, com.mojang.blaze3d.vertex.PoseStack pose,
                                      double x, double y, double z, com.mojang.math.Matrix4f projection, CallbackInfo ci) {
        if (type != net.minecraft.client.renderer.RenderType.cutout()) return;
        ModernHooks.AFTER_SOLID.run();
        GlSky.afterSolid();
    }
}
*///?}
