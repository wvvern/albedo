package dev.blockpainter.modern.mixin;

import dev.blockpainter.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shading off, part 2: every block face gets full brightness instead of darker sides and bottoms. Chunk meshing,
 * fluids and other renderers that respect the level's face shading (e.g. Sodium) all ask here.
 */
//? if >=26.1 {
@Mixin(net.minecraft.world.level.CardinalLighting.class)
//?} else {
/*@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
*///?}
public abstract class FaceShadeMixin {
    //? if >=26.1 {
    @Inject(method = "byFace", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "getShade", at = @At("HEAD"), cancellable = true)
    *///?}
    private void blockpainter$flat(CallbackInfoReturnable<Float> cir) {
        if (!ModernHooks.shading()) cir.setReturnValue(1.0f);
    }
}
