package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.renderer.entity.layers.LayerElytra;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player armor off: the elytra layer skips players, not mobs or armor stands. */
@Mixin(LayerElytra.class)
public abstract class ElytraMixin {
    @Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true)
    private void albedo$elytra(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks, float age, float yaw,
            float pitch, float scale, CallbackInfo ci) {
        if (LegacyHooks.hideElytra(entity instanceof EntityPlayer)) ci.cancel();
    }
}
