package dev.albedo.modern;

import dev.albedo.core.Albedo;
import dev.albedo.core.sky.SkyImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** The fog color that the fog mixins set, and the camera states that hide the sky. */
public final class ModernFog {
    private ModernFog() {}

    //? if >=1.21.2 {
    /** Sets the fog color for the camera of the frame, where the fog code gives no camera. */
    public static void overrideFog(org.joml.Vector4f color) {
        overrideFog(ModernSky.mainCamera(), color);
    }

    public static void overrideFog(Camera camera, org.joml.Vector4f color) {
        float[] rgb = rgb(camera);
        if (rgb != null && color != null) color.set(rgb[0], rgb[1], rgb[2], color.w);
    }
    //?} else {
    /*// The fog color for the fields of setupColor, which is also the clear color behind the world.
    public static float[] setupColor(Camera camera, float red, float green, float blue) {
        float[] rgb = rgb(camera);
        if (rgb == null) rgb = new float[] {red, green, blue};
        com.mojang.blaze3d.systems.RenderSystem.clearColor(rgb[0], rgb[1], rgb[2], 0.0F);
        return rgb;
    }
    *///?}

    /**
     * The flat sky color, or the custom sky near the horizon where the camera looks, so that the fog meets the sky.
     *
     * @return null when the fog keeps its vanilla color
     */
    private static float[] rgb(Camera camera) {
        if (fogHidesSky(camera)) return null;
        SkyImage img = Albedo.skyImage();
        if (img != null && ModernSky.vanillaWithPack()) return null;
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) img = null;
        return img != null ? img.fogRgb(ModernSky.turn(img), ModernSky.cameraYaw(), ModernSky.brightness(img)) : Albedo.currentPolicy().skyRgb();
    }

    /** True when the fog code of the game gives the camera the fog of a fluid, blindness or darkness, which hides the sky. */
    static boolean fogHidesSky(Camera camera) {
        //? if >=1.17 {
        if (camera.getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER) return true;
        //?} else {
        /*if (camera.getFluidInCamera().is(net.minecraft.tags.FluidTags.WATER)) return true;
        *///?}
        return skySkipped(camera);
    }

    /** True when the sky pass of the game draws no sky in lava, in powder snow, with blindness and with darkness; 1.17 still draws it. */
    static boolean skySkipped(Camera camera) {
        //? if >=1.17 {
        net.minecraft.world.level.material.FogType fluid = camera.getFluidInCamera();
        if (fluid == net.minecraft.world.level.material.FogType.LAVA || fluid == net.minecraft.world.level.material.FogType.POWDER_SNOW) return true;
        //?} else {
        /*if (camera.getFluidInCamera().is(net.minecraft.tags.FluidTags.LAVA)) return true;
        *///?}
        //? if >=1.21.11 {
        net.minecraft.world.entity.Entity entity = camera.entity();
        //?} else {
        /*net.minecraft.world.entity.Entity entity = camera.getEntity();
        *///?}
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) return false;
        net.minecraft.world.entity.LivingEntity living = (net.minecraft.world.entity.LivingEntity) entity;
        //? if >=1.19 {
        return living.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || living.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
        //?} else {
        /*return living.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        *///?}
    }
}
