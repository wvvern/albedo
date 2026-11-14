package dev.albedo.modern;

import dev.albedo.core.sky.CameraFluid;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;

/** Version shims for the camera states that hide the sky; {@code SkyVisibility} holds the rule. */
final class ModernCamera {
    private ModernCamera() {}

    static CameraFluid fluid(Camera camera) {
        //? if >=1.17 {
        net.minecraft.world.level.material.FogType type = camera.getFluidInCamera();
        if (type == net.minecraft.world.level.material.FogType.WATER) return CameraFluid.WATER;
        if (type == net.minecraft.world.level.material.FogType.LAVA) return CameraFluid.LAVA;
        if (type == net.minecraft.world.level.material.FogType.POWDER_SNOW) return CameraFluid.POWDER_SNOW;
        //?} else {
        /*net.minecraft.world.level.material.FluidState state = camera.getFluidInCamera();
        if (state.is(net.minecraft.tags.FluidTags.WATER)) return CameraFluid.WATER;
        if (state.is(net.minecraft.tags.FluidTags.LAVA)) return CameraFluid.LAVA;
        *///?}
        return CameraFluid.NONE;
    }

    static boolean blind(Camera camera) {
        LivingEntity living = living(camera);
        return living != null && living.hasEffect(MobEffects.BLINDNESS);
    }

    /** The darkness effect, which exists only from 1.19. */
    static boolean dark(Camera camera) {
        //? if >=1.19 {
        LivingEntity living = living(camera);
        return living != null && living.hasEffect(MobEffects.DARKNESS);
        //?} else {
        /*return false;
        *///?}
    }

    private static LivingEntity living(Camera camera) {
        //? if >=1.21.11 {
        Entity entity = camera.entity();
        //?} else {
        /*Entity entity = camera.getEntity();
        *///?}
        return entity instanceof LivingEntity ? (LivingEntity) entity : null;
    }
}
