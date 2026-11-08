package dev.worldpainter.modern.mixin;

import dev.worldpainter.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shading off, part 1: no smooth lighting (ambient occlusion) when chunks are meshed. */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.block.ModelBlockRenderer.class)
public abstract class AmbientOcclusionMixin {
    // 26.x builds a renderer per section compile and passes the AO setting in.
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static boolean worldpainter$ambientOcclusion(boolean enabled) {
        return enabled && ModernHooks.shading();
    }
}
//?} else {
/*@Mixin(net.minecraft.client.Minecraft.class)
public abstract class AmbientOcclusionMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "useAmbientOcclusion", at = @At("HEAD"), cancellable = true)
    private static void worldpainter$ambientOcclusion(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!ModernHooks.shading()) cir.setReturnValue(false);
    }
}
*///?}
