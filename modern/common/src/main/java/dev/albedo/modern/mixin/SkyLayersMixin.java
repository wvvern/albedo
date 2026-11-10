package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the custom sky in place of the vanilla sky disc, and leaves out the dark lower disc, the sunrise fan (the glow
 * layers take its place) and the stars when the sky hides them.
 */
//? if >=26.3 {
@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public abstract class SkyLayersMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void albedo$upload(com.mojang.renderpearl.api.buffers.GpuBufferSlice fog,
                               net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
        dev.albedo.modern.PearlSky.beforePass(state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD);
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void albedo$sky(com.mojang.renderpearl.api.commands.RenderPass pass, org.joml.Vector3fc color, CallbackInfo ci) {
        if (dev.albedo.modern.PearlSky.draw(pass)) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void albedo$darkDisc(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        if (ModernSky.active()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void albedo$sunrise(com.mojang.renderpearl.api.commands.RenderPass pass, com.mojang.blaze3d.vertex.PoseStack pose, float angle,
                                org.joml.Vector4fc color, CallbackInfo ci) {
        if (ModernSky.active()) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void albedo$stars(com.mojang.renderpearl.api.commands.RenderPass pass, float brightness, com.mojang.blaze3d.vertex.PoseStack pose,
                              CallbackInfo ci) {
        if (ModernSky.hidesStars()) ci.cancel();
    }
}
//?} elif >=1.21.11 {
/*@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public abstract class SkyLayersMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void albedo$sky(int color, CallbackInfo ci) {
        if (dev.albedo.modern.PrecompiledSky.draw()) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void albedo$darkDisc(CallbackInfo ci) {
        if (ModernSky.active()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void albedo$sunrise(com.mojang.blaze3d.vertex.PoseStack pose, float angle, int color, CallbackInfo ci) {
        if (ModernSky.active()) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void albedo$stars(float brightness, com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo ci) {
        if (ModernSky.hidesStars()) ci.cancel();
    }
}
*///?} else {
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class SkyLayersMixin {
    // The vertex buffer draws of renderSky are the sky disc, the stars and the dark lower disc, in this order.
    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void albedo$sky(com.mojang.blaze3d.vertex.VertexBuffer buffer, org.joml.Matrix4f modelView, org.joml.Matrix4f projection,
                            net.minecraft.client.renderer.ShaderInstance shader) {
        if (!dev.albedo.modern.GlSky.draw(modelView.get(new float[16]), projection.get(new float[16]))) {
            buffer.drawWithShader(modelView, projection, shader);
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void albedo$stars(com.mojang.blaze3d.vertex.VertexBuffer buffer, org.joml.Matrix4f modelView, org.joml.Matrix4f projection,
                              net.minecraft.client.renderer.ShaderInstance shader) {
        if (!dev.albedo.modern.GlSky.hidesStars()) buffer.drawWithShader(modelView, projection, shader);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void albedo$darkDisc(com.mojang.blaze3d.vertex.VertexBuffer buffer, org.joml.Matrix4f modelView, org.joml.Matrix4f projection,
                                 net.minecraft.client.renderer.ShaderInstance shader) {
        if (!dev.albedo.modern.GlSky.replacedDisc()) buffer.drawWithShader(modelView, projection, shader);
    }

    // The first mesh that renderSky uploads is the sunrise fan.
    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.MeshData mesh) {
        if (dev.albedo.modern.GlSky.replacedDisc()) mesh.close();
        else com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
    }
}
*///?}
