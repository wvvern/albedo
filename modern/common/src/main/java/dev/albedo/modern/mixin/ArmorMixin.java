package dev.albedo.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.albedo.modern.ModernHooks;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player armor off: the armor layer skips players, not mobs, armor stands or mannequins. */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorMixin {
    // Each selector names the descriptor so that it skips the bridge method.
    //? if >=1.21.9 {
    // Players and mannequins share AvatarRenderState, so the entity type tells them apart.
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void albedo$armor(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes, int light,
            net.minecraft.client.renderer.entity.state.HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        //? if >=26.2 {
        net.minecraft.world.entity.EntityType<?> player = net.minecraft.world.entity.EntityTypes.PLAYER;
        //?} else {
        /*net.minecraft.world.entity.EntityType<?> player = net.minecraft.world.entity.EntityType.PLAYER;
        *///?}
        if (ModernHooks.hideArmor(state.entityType == player)) ci.cancel();
    }
    //?} elif >=1.21.2 {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void albedo$armor(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
            net.minecraft.client.renderer.entity.state.HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (ModernHooks.hideArmor(state instanceof net.minecraft.client.renderer.entity.state.PlayerRenderState)) ci.cancel();
    }
    *///?} else {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void albedo$armor(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
            net.minecraft.world.entity.LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float age,
            float yRot, float xRot, CallbackInfo ci) {
        if (ModernHooks.hideArmor(entity instanceof net.minecraft.world.entity.player.Player)) ci.cancel();
    }
    *///?}
}
