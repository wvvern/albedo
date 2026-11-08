package dev.blockpainter.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blockpainter.core.Category;
import dev.blockpainter.core.BlockPainter;
import dev.blockpainter.core.texture.AtlasPaths;
import dev.blockpainter.modern.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * What the mixins call. Each method is a thin translation from Minecraft types to the core; mixins stay one-liners
 * so the per-version surface is as small as possible.
 */
public final class ModernHooks {
    private ModernHooks() {}

    public static void bootstrap() {
        if (BlockPainter.get() == null) BlockPainter.init(new ModernPlatform());
    }

    public static void clientTick() {
        BlockPainter sc = BlockPainter.get();
        if (sc != null) sc.clientTick(ModernPlatform.currentScreen() != null);
    }

    /** Atlas sprites, before mipmaps are generated. */
    public static void processSprite(String atlas, SpriteContents sprite) {
        bootstrap();
        NativeImage image = ((SpriteContentsAccessor) sprite).blockpainter$originalImage();
        processImage(AtlasPaths.spriteLocation(atlas, String.valueOf(sprite.name())), image);
    }

    /** Stand-alone textures (entities, environment, ...). */
    public static void processImage(String location, NativeImage image) {
        if (NativeImagePixels.supports(image)) BlockPainter.processTexture(location, new NativeImagePixels(image));
    }

    public static int blockTint(int original) {
        return BlockPainter.currentPolicy().removeTint(Category.BLOCKS) ? -1 : original;
    }

    /** Sodium: a tint index of -1 means "no tint", so the renderer skips the block color provider. */
    public static int blockTintIndex(int tintIndex) {
        return BlockPainter.currentPolicy().removeTint(Category.BLOCKS) ? -1 : tintIndex;
    }

    /** Embeddium: false means "no tint", so the renderer skips the block color provider. */
    public static boolean blockTinted(boolean tinted) {
        return tinted && !BlockPainter.currentPolicy().removeTint(Category.BLOCKS);
    }

    public static boolean removeFluidTint() {
        return BlockPainter.currentPolicy().removeTint(Category.FLUIDS);
    }

    /** Biome colormaps (grass/foliage); renderer-independent, so it also reaches Sodium-style renderers. */
    public static void flattenColormap(int[] pixels) {
        bootstrap();
        if (pixels != null && BlockPainter.currentPolicy().removeTint(Category.BLOCKS)) Arrays.fill(pixels, 0xFFFFFFFF);
    }

    public static boolean shading() {
        return BlockPainter.currentPolicy().shading();
    }

    public static boolean overridesSky() {
        return BlockPainter.currentPolicy().overridesSky();
    }

    public static void overrideFog(Vector4f color) {
        float[] rgb = BlockPainter.currentPolicy().skyRgb();
        if (rgb != null && color != null) color.set(rgb[0], rgb[1], rgb[2], color.w);
    }

    /** Only the brightness option is changed; the mixin wraps every option read in the lightmap code. */
    public static Object gamma(OptionInstance<?> option, Object value) {
        return option == Minecraft.getInstance().options.gamma() ? (Object) BlockPainter.currentPolicy().gamma((Double) value) : value;
    }

    public static Object entityShadows(OptionInstance<?> option, Object value) {
        return !BlockPainter.currentPolicy().shadows() && option == Minecraft.getInstance().options.entityShadows() ? Boolean.FALSE : value;
    }

    public static boolean cloudsHidden() {
        return BlockPainter.currentPolicy().cloudsHidden();
    }
}
