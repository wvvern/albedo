package dev.plainview.legacy;

import dev.plainview.core.Category;
import dev.plainview.core.Policy;
import dev.plainview.core.Plainview;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** What the 1.8.9 mixins call: thin translations from 1.8.9 types to the core. */
public final class LegacyHooks {
    private LegacyHooks() {}

    public static void bootstrap() {
        if (Plainview.get() == null) Plainview.init(new LegacyPlatform());
    }

    public static void clientTick() {
        Plainview sc = Plainview.get();
        if (sc != null) sc.clientTick(Minecraft.getMinecraft().currentScreen != null);
    }

    /**
     * @param location "minecraft:blocks/stone" (atlas sprite) or "minecraft:textures/entity/pig/pig.png"
     * @return the image to use: the input, transformed in place, or an ARGB copy of it
     */
    public static BufferedImage processImage(String location, BufferedImage image) {
        bootstrap();
        if (image == null) return null;
        BufferedImage argb = BufferedImagePixels.toArgb(image);
        return Plainview.processTexture(location, new BufferedImagePixels(argb)) ? argb : image;
    }

    public static int blockTint(int original) {
        return Plainview.currentPolicy().removeTint(Category.BLOCKS) ? 0xFFFFFF : original;
    }

    public static boolean removeFluidTint() {
        return Plainview.currentPolicy().removeTint(Category.FLUIDS);
    }

    public static void flattenColormap(int[] pixels) {
        bootstrap();
        if (pixels != null && Plainview.currentPolicy().removeTint(Category.BLOCKS)) Arrays.fill(pixels, 0xFFFFFFFF);
    }

    /** Far above the slider's maximum of 1.0: the lightmap ends up fully lit everywhere. */
    public static float gamma(float setting) {
        return Plainview.currentPolicy().fullbright() ? 16f : setting;
    }

    public static boolean shading() {
        return Plainview.currentPolicy().shading();
    }

    public static boolean shadows() {
        return Plainview.currentPolicy().shadows();
    }

    public static boolean overridesSky() {
        return Plainview.currentPolicy().overridesSky();
    }

    /** @return {r, g, b} in 0..1 or null when the sky is not overridden */
    public static float[] skyRgb() {
        Policy p = Plainview.currentPolicy();
        if (!p.overridesSky()) return null;
        int c = p.skyColor();
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }
}
