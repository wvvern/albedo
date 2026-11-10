package dev.albedo.modern.mixin;

//? if >=1.21.2 && <1.21.6 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

// Sets the flat fog color, which the frame also clears to, on the versions where computeFogColor is static.
@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class FogColorMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private static void albedo$flatFog(net.minecraft.client.Camera camera, float partialTick,
                                       net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorld,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Vector4f> cir) {
        ModernHooks.overrideFog(camera, cir.getReturnValue());
    }
}
*///?}
