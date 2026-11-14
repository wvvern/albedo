package dev.albedo.modern.mixin;

//? if gl_core {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Leaves out the sunrise fan when the custom sky replaced the sky disc, because the glow layers take its place.
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class SkySunriseMixin {
    // The first mesh that renderSky uploads is the sunrise fan.
    //? if >=1.21 {
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.MeshData mesh) {
        if (dev.albedo.modern.ModernSky.replacedDisc()) mesh.close();
        else com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
    }
    //?} elif >=1.19 {
    /^@Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer mesh) {
        if (dev.albedo.modern.ModernSky.replacedDisc()) mesh.release();
        else com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
    }
    ^///?} else {
    /^@Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;end(Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.BufferBuilder builder) {
        if (dev.albedo.modern.ModernSky.replacedDisc()) builder.popNextBuffer();
        else com.mojang.blaze3d.vertex.BufferUploader.end(builder);
    }
    ^///?}
}
*///?}
