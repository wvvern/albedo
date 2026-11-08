package dev.blockpainter.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blockpainter.modern.ModernHooks;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entity shadows off: where the renderer checks the "entity shadows" option, it reads false. */
//? if >=1.21.2 {
@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
*///?}
public abstract class ShadowMixin {
    //? if >=1.21.2 {
    @WrapOperation(method = "extractShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    //?} else {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    *///?}
    private Object blockpainter$shadows(OptionInstance<?> option, Operation<Object> original) {
        return ModernHooks.entityShadows(option, original.call(option));
    }
}
