package dev.albedo.forge.mixin;

//? if <1.19 {
/*import dev.albedo.modern.ModernHooks;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The light pipeline of Forge takes the face shade of each quad vertex from here, so that every face has full brightness when shading is off.
@Mixin(value = LightUtil.class, remap = false)
public abstract class ForgeLightUtilMixin {
    @Inject(method = "diffuseLight(FFF)F", at = @At("RETURN"), cancellable = true)
    private static void albedo$shade(float x, float y, float z, CallbackInfoReturnable<Float> cir) {
        if (!ModernHooks.shading()) cir.setReturnValue(1.0f);
    }
}
*///?}
