package dev.albedo.modern.mixin;

//? if gl_core {
/*import dev.albedo.modern.GlSky;
import dev.albedo.modern.ModernSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >=1.19.3 {
import org.joml.Matrix4f;
//?} else {
/^import com.mojang.math.Matrix4f;
^///?}

// Draws the custom sky in place of the sky disc of renderSky, and leaves out the dark lower disc, the sunrise fan (the glow
// layers take its place) and the stars when the sky hides them.
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class GlRenderSkyMixin {
    //? if >=1.19.3 {
    private static final String DRAW = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
            + "Lnet/minecraft/client/renderer/ShaderInstance;)V";
    //?} else {
    /^private static final String DRAW = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;"
            + "Lnet/minecraft/client/renderer/ShaderInstance;)V";
    ^///?}

    // The vertex buffer draws of renderSky are the sky disc, the stars and the dark lower disc, in this order.
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0, target = DRAW))
    private void albedo$sky(com.mojang.blaze3d.vertex.VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
                            net.minecraft.client.renderer.ShaderInstance shader) {
        if (!GlSky.draw(modelView, projection)) buffer.drawWithShader(modelView, projection, shader);
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 1, target = DRAW))
    private void albedo$stars(com.mojang.blaze3d.vertex.VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
                              net.minecraft.client.renderer.ShaderInstance shader) {
        if (!ModernSky.hidesStarsThisPass()) buffer.drawWithShader(modelView, projection, shader);
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 2, target = DRAW))
    private void albedo$darkDisc(com.mojang.blaze3d.vertex.VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
                                 net.minecraft.client.renderer.ShaderInstance shader) {
        if (!ModernSky.replacedDisc()) buffer.drawWithShader(modelView, projection, shader);
    }

    // The first mesh that renderSky uploads is the sunrise fan.
    //? if >=1.21 {
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.MeshData mesh) {
        if (ModernSky.replacedDisc()) mesh.close();
        else com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
    }
    //?} elif >=1.19 {
    /^@Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer mesh) {
        if (ModernSky.replacedDisc()) mesh.release();
        else com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
    }
    ^///?} else {
    /^@Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;end(Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
    private void albedo$sunrise(com.mojang.blaze3d.vertex.BufferBuilder builder) {
        if (ModernSky.replacedDisc()) builder.popNextBuffer();
        else com.mojang.blaze3d.vertex.BufferUploader.end(builder);
    }
    ^///?}
}
*///?}
