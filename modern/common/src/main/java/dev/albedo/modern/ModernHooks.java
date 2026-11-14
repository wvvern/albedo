package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HookCounter;
import dev.albedo.core.texture.AtlasPaths;
import dev.albedo.core.texture.TextureClassifier;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** The adapter that the mixins call: it turns Minecraft types into core calls. */
public final class ModernHooks {
    /** The afterSolid method of each sky family runs it. */
    static final HookCounter AFTER_SOLID = new HookCounter("afterSolid");
    public static final HookCounter SODIUM_SHADE = new HookCounter("sodiumShade");
    public static final HookCounter DRY_FOLIAGE = new HookCounter("dryFoliage");
    private static final HookCounter TINT = new HookCounter("tint");
    private static final HookCounter SODIUM_TINT = new HookCounter("sodiumTint");
    private static final HookCounter SODIUM_WATER = new HookCounter("sodiumWater");
    private static final HookCounter ITEM_ICON = new HookCounter("itemIcon");
    private static final HookCounter CLOUDS = new HookCounter("clouds");
    private static final HookCounter FULLBRIGHT = new HookCounter("fullbright");
    private static final HookCounter SHADOW = new HookCounter("shadow");
    /** The cape and elytra layers, which run only while a player or a mob with that layer is in view. */
    private static final HookCounter CAPE = new HookCounter("cape");
    private static final HookCounter ELYTRA = new HookCounter("elytra");
    /** The flat sky hook, which a loader can skip when it adds an overload of the sky pass method and calls that one. */
    private static final HookCounter SKY_PASS = new HookCounter("skyPass");
    /** The skipped window calls of the mouse grab and release, which only a harness game skips. */
    private static final HookCounter MOUSE_GRAB = new HookCounter("mouseGrab");
    /** The counters of the hooks that can miss their target with no error, which the harness command "hooks" logs. */
    static final List<HookCounter> COUNTERS = Arrays.asList(AFTER_SOLID, TINT, SODIUM_TINT, SODIUM_WATER, SODIUM_SHADE, ITEM_ICON, CLOUDS,
            DRY_FOLIAGE, FULLBRIGHT, SHADOW, CAPE, ELYTRA, SKY_PASS, MOUSE_GRAB);

    private ModernHooks() {}

    /** Whether the window calls of the mouse grab and release must not run, which is so only with the test harness. */
    public static boolean skipMouseGrab() {
        return Harness.skip(MOUSE_GRAB);
    }

    public static void bootstrap() {
        bootstrap(null);
    }

    /** Starts the mod with the config folder of the loader, or with the folder of the game when it is null. */
    public static void bootstrap(File loaderConfigDir) {
        if (Albedo.get() == null) Albedo.init(new ModernPlatform(loaderConfigDir));
    }

    public static void clientTick() {
        ModernSky.tick();
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.clientTick(ModernScreens.currentScreen() != null);
    }

    public static void frameStart() {
        ChromaAtlases.frameStart();
        ModernSky.frameStart();
    }

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
        if (texture instanceof DownloadedSkin) ((DownloadedSkin) texture).albedo$setId(id);
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

    /** Sodium: true means the fluid quads of the water get no biome color, because its renderer does not ask BiomeColors. */
    public static boolean removeSodiumWaterTint() {
        SODIUM_WATER.run();
        return removeFluidTint();
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

    /** Whether the armor layer skips an entity: a player, while the profile hides player armor. */
    public static boolean hideArmor(boolean player) {
        return player && !Albedo.currentPolicy().playerArmor();
    }

    /** Whether the elytra layer skips an entity: a player, while the profile hides player armor. */
    public static boolean hideElytra(boolean player) {
        ELYTRA.run();
        return hideArmor(player);
    }

    /** Whether the cape layer skips an entity: a player, while the profile hides player capes. */
    public static boolean hideCape(boolean player) {
        CAPE.run();
        return player && !Albedo.currentPolicy().playerCape();
    }

    public static boolean hideArmor(net.minecraft.world.entity.EntityType<?> type) {
        return hideArmor(isPlayer(type));
    }

    public static boolean hideElytra(net.minecraft.world.entity.EntityType<?> type) {
        return hideElytra(isPlayer(type));
    }

    public static boolean hideCape(net.minecraft.world.entity.EntityType<?> type) {
        return hideCape(isPlayer(type));
    }

    private static boolean isPlayer(net.minecraft.world.entity.EntityType<?> type) {
        //? if >=26.2 {
        return type == net.minecraft.world.entity.EntityTypes.PLAYER;
        //?} else {
        /*return type == net.minecraft.world.entity.EntityType.PLAYER;
        *///?}
    }

    /** The cloud option that the renderer reads in this frame, which is off while the profile hides the clouds. */
    public static net.minecraft.client.CloudStatus cloudStatus(net.minecraft.client.CloudStatus option) {
        CLOUDS.run();
        return Albedo.currentPolicy().cloudsHidden() ? net.minecraft.client.CloudStatus.OFF : option;
    }
}
