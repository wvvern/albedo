package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The item icons of the screens, which 1.21.11 and later draw once into an item atlas and again only for an animated item,
 * with @Pseudo making this a no-op on 1.21.1, where the class does not exist.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState")
public abstract class ItemIconMixin {
    @Inject(method = "isAnimated", at = @At("RETURN"), cancellable = true, require = 0)
    private void albedo$chromaMoves(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && ModernHooks.itemIconMoves()) cir.setReturnValue(true);
    }
}
