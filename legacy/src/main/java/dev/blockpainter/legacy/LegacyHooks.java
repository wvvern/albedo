package dev.blockpainter.legacy;

import dev.blockpainter.core.Category;
import dev.blockpainter.core.Policy;
import dev.blockpainter.core.BlockPainter;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** What the 1.8.9 mixins call: thin translations from 1.8.9 types to the core. */
public final class LegacyHooks {
    private LegacyHooks() {}

    public static void bootstrap() {
        if (BlockPainter.get() == null) BlockPainter.init(new LegacyPlatform());
    }

    public static void clientTick() {
        BlockPainter sc = BlockPainter.get();
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
        return BlockPainter.processTexture(location, new BufferedImagePixels(argb)) ? argb : image;
    }

    public static int blockTint(int original) {
        return BlockPainter.currentPolicy().removeTint(Category.BLOCKS) ? 0xFFFFFF : original;
    }

    public static boolean removeFluidTint() {
        return BlockPainter.currentPolicy().removeTint(Category.FLUIDS);
    }

    public static void flattenColormap(int[] pixels) {
        bootstrap();
        if (pixels != null && BlockPainter.currentPolicy().removeTint(Category.BLOCKS)) Arrays.fill(pixels, 0xFFFFFFFF);
    }

    public static float gamma(float setting) {
        return (float) BlockPainter.currentPolicy().gamma(setting);
    }

    public static boolean shading() {
        return BlockPainter.currentPolicy().shading();
    }

    public static boolean shadows() {
        return BlockPainter.currentPolicy().shadows();
    }

    public static boolean overridesSky() {
        return BlockPainter.currentPolicy().overridesSky();
    }

    /** @return {r, g, b} in 0..1 or null when the sky is not overridden */
    public static float[] skyRgb() {
        return BlockPainter.currentPolicy().skyRgb();
    }
}
