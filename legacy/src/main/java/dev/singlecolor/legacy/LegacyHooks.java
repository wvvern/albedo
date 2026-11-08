package dev.singlecolor.legacy;

import dev.singlecolor.core.Category;
import dev.singlecolor.core.Policy;
import dev.singlecolor.core.SingleColor;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** What the 1.8.9 mixins call: thin translations from 1.8.9 types to the core. */
public final class LegacyHooks {
    private LegacyHooks() {}

    public static void bootstrap() {
        if (SingleColor.get() == null) SingleColor.init(new LegacyPlatform());
    }

    public static void clientTick() {
        SingleColor sc = SingleColor.get();
        if (sc != null) sc.clientTick(Minecraft.getMinecraft().currentScreen != null);
    }

    /**
     * @param location "minecraft:blocks/stone" (atlas sprite) or "minecraft:textures/entity/pig/pig.png"
     * @return the image to use: the input, transformed in place, or an ARGB copy of it
     */
    public static BufferedImage processImage(String location, BufferedImage image) {
        bootstrap();
        if (image == null || !SingleColor.currentPolicy().enabled()) return image;
        BufferedImage argb = BufferedImagePixels.toArgb(image);
        return SingleColor.processTexture(location, new BufferedImagePixels(argb)) ? argb : image;
    }

    public static int blockTint(int original) {
        return SingleColor.currentPolicy().removeTint(Category.BLOCKS) ? 0xFFFFFF : original;
    }

    public static boolean removeFluidTint() {
        return SingleColor.currentPolicy().removeTint(Category.FLUIDS);
    }

    public static void flattenColormap(int[] pixels) {
        bootstrap();
        if (pixels != null && SingleColor.currentPolicy().removeTint(Category.BLOCKS)) Arrays.fill(pixels, 0xFFFFFFFF);
    }

    public static boolean overridesSky() {
        return SingleColor.currentPolicy().overridesSky();
    }

    /** @return {r, g, b} in 0..1 or null when the sky is not overridden */
    public static float[] skyRgb() {
        Policy p = SingleColor.currentPolicy();
        if (!p.overridesSky()) return null;
        int c = p.skyColor();
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }
}
