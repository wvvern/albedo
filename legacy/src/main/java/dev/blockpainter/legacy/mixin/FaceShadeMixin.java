package dev.blockpainter.legacy.mixin;

import dev.blockpainter.legacy.LegacyHooks;
import net.minecraft.client.renderer.block.model.FaceBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shading off, part 2: 1.8.9 bakes face brightness into block models, so every face is baked at full brightness. */
@Mixin(FaceBakery.class)
public abstract class FaceShadeMixin {
    @Inject(method = "getFaceBrightness", at = @At("HEAD"), cancellable = true)
    private void blockpainter$flat(CallbackInfoReturnable<Float> cir) {
        if (!LegacyHooks.shading()) cir.setReturnValue(1.0f);
    }
}
