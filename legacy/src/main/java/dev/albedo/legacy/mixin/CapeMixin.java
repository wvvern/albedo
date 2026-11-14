package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player cape off: the cape layer, which only players have, draws nothing. */
@Mixin(LayerCape.class)
public abstract class CapeMixin {
    // The descriptor skips the bridge method that takes an EntityLivingBase.
    @Inject(method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void albedo$cape(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float age, float yaw,
            float pitch, float scale, CallbackInfo ci) {
        if (LegacyHooks.hideCape()) ci.cancel();
    }
}
