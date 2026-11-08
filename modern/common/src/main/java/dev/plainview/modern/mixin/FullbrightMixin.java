package dev.plainview.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.plainview.modern.ModernHooks;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright: while the lightmap is computed, the brightness (gamma) option reads as far above its maximum. Every
 * option read in that method is wrapped and only the gamma one is changed, so nothing depends on call order.
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.LightmapRenderStateExtractor.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.LightTexture.class)
*///?}
public abstract class FullbrightMixin {
    //? if >=26.1 {
    @WrapOperation(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    //?} else {
    /*@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    *///?}
    private Object plainview$gamma(OptionInstance<?> option, Operation<Object> original) {
        return ModernHooks.gamma(option, original.call(option));
    }
}
