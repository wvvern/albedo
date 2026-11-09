package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.texture.AtlasPaths;
import dev.albedo.core.texture.TextureClassifier;
import dev.albedo.core.texture.TextureStore;
import dev.albedo.modern.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.joml.Vector4f;

/**
 * What the mixins call, as thin translations from Minecraft types to the core so that the mixins stay one-liners and
 * the per-version surface stays small.
 */
public final class ModernHooks {
    private ModernHooks() {}

    public static void bootstrap() {
        if (Albedo.get() == null) Albedo.init(new ModernPlatform());
    }

    public static void clientTick() {
        ModernSky.tick();
        Albedo sc = Albedo.get();
        if (sc != null) sc.clientTick(ModernPlatform.currentScreen() != null);
    }

    /**
     * Atlas sprites, before mipmaps are generated.
     *
     * @param atlas the location of the atlas texture
     */
    public static void processSprite(Object atlas, SpriteContents sprite) {
        bootstrap();
        NativeImage image = ((SpriteContentsAccessor) sprite).albedo$originalImage();
        processImage(AtlasPaths.spriteLocation(String.valueOf(atlas), String.valueOf(sprite.name())), image,
                TextureUploads.sprite(atlas, sprite));
    }

    //? if >=1.21.4 {
    /** Stand-alone textures (entities, environment, ...), as TextureContents.load gives them. */
    public static void processTexture(Object location, NativeImage image) {
        processImage(String.valueOf(location), image, TextureUploads.texture(location));
    }
    //?} else {
    /*// Stand-alone textures (entities, environment, ...), as SimpleTexture uploads them.
    public static void processTexture(Object location, NativeImage image, net.minecraft.client.renderer.texture.SimpleTexture texture,
                                      boolean blur, boolean clamp) {
        // A skin placeholder loads the default skin, and must not take the place of the real default skin.
        TextureStore.Upload upload = texture instanceof net.minecraft.client.renderer.texture.HttpTexture ? null
                : TextureUploads.texture(location, texture, blur, clamp);
        processImage(String.valueOf(location), image, upload);
    }
    *///?}

    private static boolean processImage(String location, NativeImage image, TextureStore.Upload upload) {
        return NativeImagePixels.supports(image) && Albedo.processTexture(location, new NativeImagePixels(image), upload);
    }

    //? if >=1.21.4 {
    /** A texture that TextureManager.register keeps, where only a downloaded skin, cape or elytra changes. */
    public static void registered(Object id, net.minecraft.client.renderer.texture.AbstractTexture texture) {
        String name = String.valueOf(id);
        if (!(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture dynamic) || !TextureClassifier.isDownloaded(name)) return;
        bootstrap();
        // The texture made its GPU copy in its constructor, so a changed image goes up again.
        if (processImage(name, dynamic.getPixels(), TextureUploads.downloaded(id, dynamic))) dynamic.upload();
    }
    //?} else {
    /*// Gives a downloaded skin its name before it loads, because TextureManager.register loads it.
    public static void registering(Object id, net.minecraft.client.renderer.texture.AbstractTexture texture) {
        if (texture instanceof DownloadedSkin skin) skin.albedo$setId(id);
    }

    // A downloaded skin, cape or elytra, as HttpTexture decodes it before its upload.
    public static void processDownloaded(Object id, NativeImage image, DownloadedSkin texture) {
        String name = String.valueOf(id);
        if (id == null || !TextureClassifier.isDownloaded(name)) return;
        bootstrap();
        processImage(name, image, TextureUploads.downloaded(id, texture));
    }
    *///?}

    /** A texture that the game closes, whose original the core then drops. */
    public static void released(Object id) {
        Albedo.forgetTexture(String.valueOf(id));
    }

    public static int blockTint(int original) {
        return Albedo.currentPolicy().removeTint(Category.BLOCKS) ? -1 : original;
    }

    /** Sodium: a tint index of -1 means "no tint", so the renderer skips the block color provider. */
    public static int blockTintIndex(int tintIndex) {
        return Albedo.currentPolicy().removeTint(Category.BLOCKS) ? -1 : tintIndex;
    }

    /** Embeddium: false means "no tint", so the renderer skips the block color provider. */
    public static boolean blockTinted(boolean tinted) {
        return tinted && !Albedo.currentPolicy().removeTint(Category.BLOCKS);
    }

    public static boolean removeFluidTint() {
        return Albedo.currentPolicy().removeTint(Category.FLUIDS);
    }

    /**
     * Biome colormaps ("grass", "foliage", "dry_foliage"), repainted in the array that the game keeps, which also
     * reaches Sodium-style renderers.
     */
    public static void colormap(String name, int[] pixels) {
        bootstrap();
        Albedo.processColormap("minecraft:textures/colormap/" + name + ".png", pixels);
    }

    public static boolean shading() {
        return Albedo.currentPolicy().shading();
    }

    public static boolean overridesSky() {
        return Albedo.currentPolicy().overridesSky();
    }

    /** The flat sky color, or the custom sky near the horizon where the camera looks, so that the fog meets the sky. */
    public static void overrideFog(Vector4f color) {
        SkyImage img = Albedo.skyImage();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) img = null;
        float[] rgb = img != null ? img.fogRgb(ModernSky.turn(img), ModernSky.cameraYaw(), ModernSky.brightness(img)) : Albedo.currentPolicy().skyRgb();
        if (rgb != null && color != null) color.set(rgb[0], rgb[1], rgb[2], color.w);
    }

    /** Only the brightness option is changed; the mixin wraps every option read in the lightmap code. */
    public static Object gamma(OptionInstance<?> option, Object value) {
        return option == Minecraft.getInstance().options.gamma() ? (Object) Albedo.currentPolicy().gamma((Double) value) : value;
    }

    public static Object entityShadows(OptionInstance<?> option, Object value) {
        return !Albedo.currentPolicy().shadows() && option == Minecraft.getInstance().options.entityShadows() ? Boolean.FALSE : value;
    }

    public static boolean cloudsHidden() {
        return Albedo.currentPolicy().cloudsHidden();
    }
}
