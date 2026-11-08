package dev.worldpainter.legacy;

import dev.worldpainter.core.Category;
import dev.worldpainter.core.Policy;
import dev.worldpainter.core.WorldPainter;
import dev.worldpainter.core.texture.TextureStore;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;

/** What the 1.8.9 mixins call: thin translations from 1.8.9 types to the core. */
public final class LegacyHooks {
    private LegacyHooks() {}

    public static void bootstrap() {
        if (WorldPainter.get() == null) WorldPainter.init(new LegacyPlatform());
    }

    public static void clientTick() {
        WorldPainter sc = WorldPainter.get();
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
        return WorldPainter.processTexture(location, new BufferedImagePixels(argb), NO_REPAINT) ? argb : image;
    }

    /** Until each texture kind has its own upload, a repaint falls back to a resource reload. */
    private static final TextureStore.Upload NO_REPAINT = new TextureStore.Upload() {
        @Override
        public boolean upload(dev.worldpainter.core.PixelBuffer pixels) {
            throw new UnsupportedOperationException("no repaint yet");
        }
    };

    public static int blockTint(int original) {
        return WorldPainter.currentPolicy().removeTint(Category.BLOCKS) ? 0xFFFFFF : original;
    }

    /** OptiFine CustomColors: -1 means "no custom color" and stays -1. */
    public static int optiFineBlockTint(int original) {
        return original == -1 ? -1 : blockTint(original);
    }

    public static boolean removeFluidTint() {
        return WorldPainter.currentPolicy().removeTint(Category.FLUIDS);
    }

    public static int fluidTint(int original) {
        return removeFluidTint() ? 0xFFFFFF : original;
    }

    public static float faceBrightness(float original) {
        return shading() ? original : 1.0f;
    }

    /** Biome colormaps ("grass", "foliage"). The game keeps the array, so a repaint changes it in place. */
    public static void colormap(String name, int[] pixels) {
        bootstrap();
        WorldPainter.processColormap("minecraft:textures/colormap/" + name + ".png", pixels);
    }

    public static float gamma(float setting) {
        return (float) WorldPainter.currentPolicy().gamma(setting);
    }

    public static boolean shading() {
        return WorldPainter.currentPolicy().shading();
    }

    public static boolean shadows() {
        return WorldPainter.currentPolicy().shadows();
    }

    public static boolean overridesSky() {
        return WorldPainter.currentPolicy().overridesSky();
    }

    /** @return {r, g, b} in 0..1 or null when the sky is not overridden */
    public static float[] skyRgb() {
        return WorldPainter.currentPolicy().skyRgb();
    }
}
