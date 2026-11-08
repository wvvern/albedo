package dev.worldpainter.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.worldpainter.core.Category;
import dev.worldpainter.core.WorldPainter;
import dev.worldpainter.core.texture.AtlasPaths;
import dev.worldpainter.core.texture.TextureStore;
import dev.worldpainter.modern.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.joml.Vector4f;

/**
 * What the mixins call. Each method is a thin translation from Minecraft types to the core; mixins stay one-liners
 * so the per-version surface is as small as possible.
 */
public final class ModernHooks {
    private ModernHooks() {}

    public static void bootstrap() {
        if (WorldPainter.get() == null) WorldPainter.init(new ModernPlatform());
    }

    public static void clientTick() {
        WorldPainter sc = WorldPainter.get();
        if (sc != null) sc.clientTick(ModernPlatform.currentScreen() != null);
    }

    /** Atlas sprites, before mipmaps are generated. */
    public static void processSprite(String atlas, SpriteContents sprite) {
        bootstrap();
        NativeImage image = ((SpriteContentsAccessor) sprite).worldpainter$originalImage();
        processImage(AtlasPaths.spriteLocation(atlas, String.valueOf(sprite.name())), image);
    }

    /** Stand-alone textures (entities, environment, ...). */
    public static void processImage(String location, NativeImage image) {
        if (NativeImagePixels.supports(image)) WorldPainter.processTexture(location, new NativeImagePixels(image), NO_REPAINT);
    }

    /** Until each texture kind has its own upload, a repaint falls back to a resource reload. */
    private static final TextureStore.Upload NO_REPAINT = new TextureStore.Upload() {
        @Override
        public boolean upload(dev.worldpainter.core.PixelBuffer pixels) {
            throw new UnsupportedOperationException("no repaint yet");
        }
    };

    public static int blockTint(int original) {
        return WorldPainter.currentPolicy().removeTint(Category.BLOCKS) ? -1 : original;
    }

    /** Sodium: a tint index of -1 means "no tint", so the renderer skips the block color provider. */
    public static int blockTintIndex(int tintIndex) {
        return WorldPainter.currentPolicy().removeTint(Category.BLOCKS) ? -1 : tintIndex;
    }

    /** Embeddium: false means "no tint", so the renderer skips the block color provider. */
    public static boolean blockTinted(boolean tinted) {
        return tinted && !WorldPainter.currentPolicy().removeTint(Category.BLOCKS);
    }

    public static boolean removeFluidTint() {
        return WorldPainter.currentPolicy().removeTint(Category.FLUIDS);
    }

    /**
     * Biome colormaps ("grass", "foliage", "dry_foliage"); renderer-independent, so they also reach Sodium-style
     * renderers. The game keeps the array, so a repaint changes it in place.
     */
    public static void colormap(String name, int[] pixels) {
        bootstrap();
        WorldPainter.processColormap("minecraft:textures/colormap/" + name + ".png", pixels);
    }

    public static boolean shading() {
        return WorldPainter.currentPolicy().shading();
    }

    public static boolean overridesSky() {
        return WorldPainter.currentPolicy().overridesSky();
    }

    public static void overrideFog(Vector4f color) {
        float[] rgb = WorldPainter.currentPolicy().skyRgb();
        if (rgb != null && color != null) color.set(rgb[0], rgb[1], rgb[2], color.w);
    }

    /** Only the brightness option is changed; the mixin wraps every option read in the lightmap code. */
    public static Object gamma(OptionInstance<?> option, Object value) {
        return option == Minecraft.getInstance().options.gamma() ? (Object) WorldPainter.currentPolicy().gamma((Double) value) : value;
    }

    public static Object entityShadows(OptionInstance<?> option, Object value) {
        return !WorldPainter.currentPolicy().shadows() && option == Minecraft.getInstance().options.entityShadows() ? Boolean.FALSE : value;
    }

    public static boolean cloudsHidden() {
        return WorldPainter.currentPolicy().cloudsHidden();
    }
}
