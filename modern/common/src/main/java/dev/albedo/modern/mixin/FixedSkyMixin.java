package dev.albedo.modern.mixin;

//? if gl_fixed {
/*import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Matrix4f;
import dev.albedo.modern.FixedSky;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.ModernSky;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The sky hooks of the fixed function versions: the custom sky takes the place of the sky disc, and the sky that waits for the
// solid world draws after the cutout layer.
@Mixin(LevelRenderer.class)
public abstract class FixedSkyMixin {
    // The vertex buffer draws of renderSky are the sky disc, the stars and the dark lower disc, in this order.
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw(Lcom/mojang/math/Matrix4f;I)V"))
    private void albedo$sky(VertexBuffer buffer, Matrix4f matrix, int mode) {
        if (!FixedSky.draw(matrix)) buffer.draw(matrix, mode);
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw(Lcom/mojang/math/Matrix4f;I)V"))
    private void albedo$stars(VertexBuffer buffer, Matrix4f matrix, int mode) {
        if (!ModernSky.hidesStarsThisPass()) buffer.draw(matrix, mode);
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw(Lcom/mojang/math/Matrix4f;I)V"))
    private void albedo$darkDisc(VertexBuffer buffer, Matrix4f matrix, int mode) {
        if (!ModernSky.replacedDisc()) buffer.draw(matrix, mode);
    }

    // The first mesh that renderSky uploads is the sunrise fan, and the glow layers of the custom sky take its place.
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;end(Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
    private void albedo$sunrise(BufferBuilder builder) {
        // The builder keeps the mesh until the next upload takes it, so a mesh that does not draw is taken here.
        if (ModernSky.replacedDisc()) builder.popNextBuffer();
        else BufferUploader.end(builder);
    }

    // The cutout layer is the last solid layer of the world.
    @Inject(method = "renderChunkLayer", at = @At("TAIL"), require = 0)
    private void albedo$skyAfterSolid(RenderType type, PoseStack pose, double x, double y, double z, CallbackInfo ci) {
        if (type != RenderType.cutout()) return;
        ModernHooks.AFTER_SOLID.run();
        FixedSky.afterSolid();
    }
}
*///?}
