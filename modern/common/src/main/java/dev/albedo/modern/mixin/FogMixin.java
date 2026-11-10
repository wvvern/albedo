package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** Sets the flat fog color, which doubles as the clear color behind the world; {@code StaticFogMixin} has 1.21.2 to 1.21.5. */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class FogMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                     net.minecraft.client.multiplayer.ClientLevel level, int renderDistance,
                                     float darkenWorld, org.joml.Vector4f color,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ModernHooks.overrideFog(camera, color);
    }
}
//?} elif >=1.21.6 {
/*@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class FogMixin {
    // The parameters change between the versions, so the handler takes none of them and asks for the camera of the frame.
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void albedo$flatFog(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Vector4f> cir) {
        ModernHooks.overrideFog(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera(), cir.getReturnValue());
    }
}
*///?} elif <1.21.2 {
/*@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class FogMixin {
    @org.spongepowered.asm.mixin.Shadow private static float fogRed;
    @org.spongepowered.asm.mixin.Shadow private static float fogGreen;
    @org.spongepowered.asm.mixin.Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                       net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float bossColorModifier,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        org.joml.Vector4f c = new org.joml.Vector4f(fogRed, fogGreen, fogBlue, 0f);
        ModernHooks.overrideFog(camera, c);
        fogRed = c.x;
        fogGreen = c.y;
        fogBlue = c.z;
        com.mojang.blaze3d.systems.RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
*///?}
