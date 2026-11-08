package dev.worldpainter.legacy.mixin;

import dev.worldpainter.legacy.LegacyHooks;
import net.minecraft.client.renderer.block.model.FaceBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shading off, part 2: 1.8.9 bakes face brightness into block models, so every face is baked at full brightness.
 * OptiFine makes the method static, see {@link OptiFineRenderMixin}.
 */
@Mixin(FaceBakery.class)
public abstract class FaceShadeVanillaMixin {
    @Inject(method = "getFaceBrightness", at = @At("HEAD"), cancellable = true)
    private void worldpainter$flat(CallbackInfoReturnable<Float> cir) {
        if (!LegacyHooks.shading()) cir.setReturnValue(1.0f);
    }
}
