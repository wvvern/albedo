package dev.albedo.modern.mixin;

//? if >=1.21.2 && <1.21.6 {
/*import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^* Sets the flat fog color, which doubles as the clear color behind the world, where computeFogColor is static. ^/
@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class StaticFogMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private static void albedo$flatFog(CallbackInfoReturnable<org.joml.Vector4f> cir) {
        ModernHooks.overrideFog(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera(), cir.getReturnValue());
    }
}
*///?}
