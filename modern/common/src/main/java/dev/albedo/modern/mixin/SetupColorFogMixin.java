package dev.albedo.modern.mixin;

//? if <1.21.2 {
/*import dev.albedo.modern.ModernFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sets the flat fog color on the versions before 1.21.2, where setupColor keeps the fog color in three static fields.
@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class SetupColorFogMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                       net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float bossColorModifier,
                                       CallbackInfo ci) {
        float[] rgb = ModernFog.setupColor(camera, fogRed, fogGreen, fogBlue);
        fogRed = rgb[0];
        fogGreen = rgb[1];
        fogBlue = rgb[2];
    }
}
*///?}
