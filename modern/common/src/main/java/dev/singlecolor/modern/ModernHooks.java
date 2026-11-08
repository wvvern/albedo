package dev.singlecolor.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.singlecolor.core.Category;
import dev.singlecolor.core.Policy;
import dev.singlecolor.core.SingleColor;
import dev.singlecolor.core.config.Appearance;
import dev.singlecolor.core.texture.AtlasPaths;
import dev.singlecolor.core.texture.TextureClassifier;
import dev.singlecolor.modern.mixin.SpriteContentsAccessor;
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
    /** Far above the slider's maximum of 1.0: the lightmap ends up fully lit everywhere. */
    private static final Double FULLBRIGHT_GAMMA = 16.0;

    private ModernHooks() {}

    public static void bootstrap() {
        if (SingleColor.get() == null) SingleColor.init(new ModernPlatform());
    }

    public static void clientTick() {
        SingleColor sc = SingleColor.get();
        if (sc != null) sc.clientTick(ModernPlatform.currentScreen() != null);
    }

    /** Atlas sprites, before mipmaps are generated. */
    public static void processSprite(String atlas, SpriteContents sprite) {
        bootstrap();
        NativeImage image = ((SpriteContentsAccessor) sprite).singlecolor$originalImage();
        processImage(AtlasPaths.spriteLocation(atlas, String.valueOf(sprite.name())), image);
    }

    /** Stand-alone textures (entities, environment, ...). */
    public static void processImage(String location, NativeImage image) {
        if (NativeImagePixels.supports(image)) SingleColor.processTexture(location, new NativeImagePixels(image));
    }

    public static int blockTint(int original) {
        return SingleColor.currentPolicy().removeTint(Category.BLOCKS) ? -1 : original;
    }

    public static boolean removeFluidTint() {
        return SingleColor.currentPolicy().removeTint(Category.FLUIDS);
    }

    /** Biome colormaps (grass/foliage); renderer-independent, so it also reaches Sodium-style renderers. */
    public static void flattenColormap(int[] pixels) {
        bootstrap();
        if (pixels != null && SingleColor.currentPolicy().removeTint(Category.BLOCKS)) Arrays.fill(pixels, 0xFFFFFFFF);
    }

    public static boolean shading() {
        return SingleColor.currentPolicy().shading();
    }

    public static boolean overridesSky() {
        return SingleColor.currentPolicy().overridesSky();
    }

    public static void overrideFog(Vector4f color) {
        Policy p = SingleColor.currentPolicy();
        if (!p.overridesSky() || color == null) return;
        int c = p.skyColor();
        color.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, color.w);
    }

    public static Object gamma(OptionInstance<?> option, Object value) {
        return SingleColor.currentPolicy().fullbright() && option == Minecraft.getInstance().options.gamma() ? FULLBRIGHT_GAMMA : value;
    }

    public static Object entityShadows(OptionInstance<?> option, Object value) {
        return !SingleColor.currentPolicy().shadows() && option == Minecraft.getInstance().options.entityShadows() ? Boolean.FALSE : value;
    }

    public static boolean cloudsHidden() {
        return SingleColor.currentPolicy()
                .appearanceFor(TextureClassifier.classify("minecraft:textures/environment/clouds.png"))
                .mode() == Appearance.Mode.HIDDEN;
    }
}
