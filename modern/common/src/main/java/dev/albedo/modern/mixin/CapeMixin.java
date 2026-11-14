package dev.albedo.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player cape off: the cape layer skips players, not mannequins. */
@Mixin(CapeLayer.class)
public abstract class CapeMixin {
    // Each selector names the descriptor so that it skips the bridge method.
    //? if >=1.21.9 {
    // Players and mannequins share AvatarRenderState, so the entity type tells them apart.
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void albedo$cape(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes, int light,
            net.minecraft.client.renderer.entity.state.AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        //? if >=26.2 {
        net.minecraft.world.entity.EntityType<?> player = net.minecraft.world.entity.EntityTypes.PLAYER;
        //?} else {
        /*net.minecraft.world.entity.EntityType<?> player = net.minecraft.world.entity.EntityType.PLAYER;
        *///?}
        if (ModernHooks.hideCape(state.entityType == player)) ci.cancel();
    }
    //?} elif >=1.21.2 {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void albedo$cape(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
            net.minecraft.client.renderer.entity.state.PlayerRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (ModernHooks.hideCape(true)) ci.cancel();
    }
    *///?} else {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void albedo$cape(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
            net.minecraft.client.player.AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float age,
            float yRot, float xRot, CallbackInfo ci) {
        if (ModernHooks.hideCape(true)) ci.cancel();
    }
    *///?}
}
