package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.dev.HookCounter;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.texture.AtlasPaths;
import dev.albedo.core.texture.TextureClassifier;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
//? if >=1.19.3 {
import org.joml.Vector4f;
//?} else {
/*import com.mojang.math.Vector4f;
*///?}

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * What the mixins call, as thin translations from Minecraft types to the core so that the mixins stay one-liners and
 * the per-version surface stays small.
 */
public final class ModernHooks {
    public static final HookCounter AFTER_SOLID = new HookCounter("afterSolid");
    public static final HookCounter SODIUM_SHADE = new HookCounter("sodiumShade");
    public static final HookCounter DRY_FOLIAGE = new HookCounter("dryFoliage");
    private static final HookCounter TINT = new HookCounter("tint");
    private static final HookCounter SODIUM_TINT = new HookCounter("sodiumTint");
    private static final HookCounter ITEM_ICON = new HookCounter("itemIcon");
    private static final HookCounter CLOUDS = new HookCounter("clouds");
    private static final HookCounter FULLBRIGHT = new HookCounter("fullbright");
    private static final HookCounter SHADOW = new HookCounter("shadow");
    /** The flat sky hook, which a loader can skip when it adds an overload of the sky pass method and calls that one. */
    private static final HookCounter SKY_PASS = new HookCounter("skyPass");
    /** The counters of the hooks that can miss their target with no error, which the harness command "hooks" logs. */
    static final List<HookCounter> COUNTERS = Arrays.asList(AFTER_SOLID, TINT, SODIUM_TINT, SODIUM_SHADE, ITEM_ICON, CLOUDS, DRY_FOLIAGE,
            FULLBRIGHT, SHADOW, SKY_PASS);

    private ModernHooks() {}

    public static void bootstrap() {
        bootstrap(null);
    }

    /** Starts the mod with the config folder of the loader, or with the folder of the game when it is null. */
    public static void bootstrap(File loaderConfigDir) {
        if (Albedo.get() == null) Albedo.init(new ModernPlatform(loaderConfigDir));
    }

    public static void clientTick() {
        ModernSky.tick();
        Albedo sc = Albedo.get();
        if (sc != null) sc.clientTick(ModernPlatform.currentScreen() != null);
    }

    /** The start of a frame, before it draws anything. */
    public static void frameStart() {
        ChromaAtlases.frameStart();
        ModernSky.frameStart();
    }

    /** The end of a frame, after it drew the world and the screens. */
    public static void frameEnd() {
        ChromaAtlases.frameEnd();
    }

    /** Whether an item icon of a screen must draw again in each frame, as an animated item does, because Chroma colors move. */
    public static boolean itemIconMoves() {
        ITEM_ICON.run();
        return ChromaAtlases.moving();
    }

    /**
     * Atlas sprites, before mipmaps are generated.
     *
     * @param atlas the location of the atlas texture
     */
    public static void processSprite(Object atlas, SpriteAccess sprite) {
        bootstrap();
        processImage(AtlasPaths.spriteLocation(String.valueOf(atlas), sprite.name()), sprite.image(), TextureUploads.sprite(atlas, sprite));
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
        TINT.run();
        return Albedo.currentPolicy().removeTint(Category.BLOCKS) ? -1 : original;
    }

    /** Sodium: a tint index of -1 means "no tint", so the renderer skips the block color provider. */
    public static int blockTintIndex(int tintIndex) {
        SODIUM_TINT.run();
        return Albedo.currentPolicy().removeTint(Category.BLOCKS) ? -1 : tintIndex;
    }

    /** Embeddium: false means "no tint", so the renderer skips the block color provider. */
    public static boolean blockTinted(boolean tinted) {
        SODIUM_TINT.run();
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
        Albedo.processColormap(name, pixels);
    }

    public static boolean shading() {
        return Albedo.currentPolicy().shading();
    }

    public static boolean overridesSky() {
        SKY_PASS.run();
        return Albedo.currentPolicy().overridesSky();
    }

    /** The flat sky color, or the custom sky near the horizon where the camera looks, so that the fog meets the sky. */
    public static void overrideFog(net.minecraft.client.Camera camera, Vector4f color) {
        if (specialFog(camera)) return;
        SkyImage img = Albedo.skyImage();
        if (img != null && ModernSky.vanillaWithPack()) return;
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) img = null;
        float[] rgb = img != null ? img.fogRgb(ModernSky.turn(img), ModernSky.cameraYaw(), ModernSky.brightness(img)) : Albedo.currentPolicy().skyRgb();
        if (rgb == null || color == null) return;
        //? if >=1.19.3 {
        color.set(rgb[0], rgb[1], rgb[2], color.w);
        //?} else {
        /*color.set(rgb[0], rgb[1], rgb[2], color.w());
        *///?}
    }

    /** True when the fog code of the game gives the camera the fog of a fluid, blindness or darkness, which hides the sky. */
    private static boolean specialFog(net.minecraft.client.Camera camera) {
        net.minecraft.world.level.material.FogType fluid = camera.getFluidInCamera();
        if (fluid == net.minecraft.world.level.material.FogType.WATER || fluid == net.minecraft.world.level.material.FogType.LAVA
                || fluid == net.minecraft.world.level.material.FogType.POWDER_SNOW) return true;
        //? if >=1.21.11 {
        net.minecraft.world.entity.Entity entity = camera.entity();
        //?} else {
        /*net.minecraft.world.entity.Entity entity = camera.getEntity();
        *///?}
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) return false;
        net.minecraft.world.entity.LivingEntity living = (net.minecraft.world.entity.LivingEntity) entity;
        return living.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || living.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
    }

    //? if >=1.19 {
    /** Only the brightness option is changed; the mixin wraps every option read in the lightmap code. */
    public static Object gamma(net.minecraft.client.OptionInstance<?> option, Object value) {
        FULLBRIGHT.run();
        return option == Minecraft.getInstance().options.gamma() ? (Object) Albedo.currentPolicy().gamma((Double) value) : value;
    }

    public static Object entityShadows(net.minecraft.client.OptionInstance<?> option, Object value) {
        SHADOW.run();
        return !Albedo.currentPolicy().shadows() && option == Minecraft.getInstance().options.entityShadows() ? Boolean.FALSE : value;
    }
    //?} else {
    /*// The brightness option as the lightmap code reads its field.
    public static double gamma(double value) {
        FULLBRIGHT.run();
        return Albedo.currentPolicy().gamma(value);
    }

    // The "entity shadows" option as the entity renderer reads its field.
    public static boolean entityShadows(boolean value) {
        SHADOW.run();
        return value && Albedo.currentPolicy().shadows();
    }
    *///?}

    /** The cloud option that the renderer reads in this frame, which is off while the profile hides the clouds. */
    public static net.minecraft.client.CloudStatus cloudStatus(net.minecraft.client.CloudStatus option) {
        CLOUDS.run();
        return Albedo.currentPolicy().cloudsHidden() ? net.minecraft.client.CloudStatus.OFF : option;
    }
}
