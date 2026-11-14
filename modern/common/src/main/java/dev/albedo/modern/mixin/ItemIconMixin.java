package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item icons of the screens, which the game draws once into an item atlas from 1.21.6 and again only for an animated item.
 * {@code @Pseudo}: the class exists from 1.21.4.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState")
public abstract class ItemIconMixin {
    @Inject(method = "isAnimated", at = @At("RETURN"), cancellable = true, require = 0)
    private void albedo$chromaMoves(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && ModernHooks.itemIconMoves()) cir.setReturnValue(true);
    }
}
