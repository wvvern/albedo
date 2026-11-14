package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Sets the flat fog color from 1.21.11, which is also the clear color behind the world; {@code SetupColorFogMixin},
 * {@code StaticFogMixin} and {@code FogCameraMixin} cover the versions before 1.21.11.
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class FogMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                     net.minecraft.client.multiplayer.ClientLevel level, int renderDistance,
                                     float darkenWorld, org.joml.Vector4f color,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ModernFog.overrideFog(camera, color);
    }
}
//?} elif >=1.21.11 {
/*@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class FogMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                     net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorld,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Vector4f> cir) {
        ModernFog.overrideFog(camera, cir.getReturnValue());
    }
}
*///?}
