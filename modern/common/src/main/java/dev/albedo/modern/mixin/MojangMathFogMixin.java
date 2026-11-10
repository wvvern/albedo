package dev.albedo.modern.mixin;

//? if <1.19.3 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sets the flat fog color on the versions whose vectors are com.mojang.math types, as FogMixin does.
@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class MojangMathFogMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                       net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float bossColorModifier,
                                       CallbackInfo ci) {
        com.mojang.math.Vector4f c = new com.mojang.math.Vector4f(fogRed, fogGreen, fogBlue, 0f);
        ModernHooks.overrideFog(camera, c);
        fogRed = c.x();
        fogGreen = c.y();
        fogBlue = c.z();
        com.mojang.blaze3d.systems.RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
*///?}
