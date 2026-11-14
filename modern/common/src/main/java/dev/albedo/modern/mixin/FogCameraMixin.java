package dev.albedo.modern.mixin;

//? if >=1.21.6 && <1.21.11 {
/*import dev.albedo.modern.ModernFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

// Sets the flat fog color on 1.21.6 to 1.21.10, where computeFogColor has one more parameter on 1.21.8 and 1.21.10, so the
// handler takes none of them and ModernFog reads the camera of the frame.
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class FogCameraMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void albedo$flatFog(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Vector4f> cir) {
        ModernFog.overrideFog(cir.getReturnValue());
    }
}
*///?}
