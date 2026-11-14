package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HookCounter;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** What the mixins call: thin translations from the game types to the core. */
public final class LegacyHooks {
    public static final HookCounter AFTER_SOLID = new HookCounter("afterSolid");
    public static final HookCounter BEFORE_CLOUDS = new HookCounter("beforeClouds");
    /** The block tint hook of the light pipeline that draws the blocks: Forge or vanilla. */
    public static final HookCounter TINT = new HookCounter("tint");
    /** The face shade hook of the light pipeline that draws the blocks: Forge or vanilla. */
    public static final HookCounter SHADE = new HookCounter("shade");
    public static final HookCounter RELOAD_STEP = new HookCounter("reloadStep");
    public static final HookCounter TEXTURE_UPLOAD = new HookCounter("textureUpload");
    /** The skipped LWJGL calls of the mouse grab and release, which only a harness game skips. */
    private static final HookCounter MOUSE_GRAB = new HookCounter("mouseGrab");
    /** The cape and elytra layers, which run only while a player or a mob with that layer is in view; 1.8.9 has no elytra. */
    private static final HookCounter CAPE = new HookCounter("cape");
    private static final HookCounter ELYTRA = new HookCounter("elytra");
    /** The counters of the hooks that can miss their target with no error, which the harness command "hooks" logs. */
    static final List<HookCounter> COUNTERS = Arrays.asList(AFTER_SOLID, BEFORE_CLOUDS, TINT, SHADE, RELOAD_STEP, TEXTURE_UPLOAD, MOUSE_GRAB,
            CAPE, ELYTRA);
    /** The folder of the texture that SkinManager.loadSkin loads on this thread now ("skins" or "capes"), or null. */
    private static final ThreadLocal<String> LOADING_SKIN = new ThreadLocal<String>();
    /** The host of the last connection to a server, which stays until the next connection. */
    private static volatile String connectedHost;

    private LegacyHooks() {}

    /** Whether the LWJGL calls of the mouse grab and release must not run, which is so only with the test harness. */
    public static boolean skipMouseGrab() {
        return Harness.skip(MOUSE_GRAB);
    }

    public static void connecting(String host) {
        connectedHost = host;
    }

    static String connectedHost() {
        return connectedHost;
    }

    public static void bootstrap() {
        if (Albedo.get() == null) Albedo.init(new LegacyPlatform());
    }

    public static void clientTick() {
        LegacySky.tick();
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.clientTick(Minecraft.getMinecraft().currentScreen != null);
    }

    /**
     * Changes the animation strip of a block atlas sprite before the game makes frames and mip levels from it.
     *
     * @return the changed strip, or the input when nothing changes
     */
    static BufferedImage processSprite(String name, BufferedImage strip, TextureStore.Upload upload) {
        bootstrap();
        // A repaint loads the sprite with pixels that are already done: nothing to keep or change.
        if (strip == null || Albedo.repainting()) return strip;
        ReloadScreen.load();
        // OptiFine "Multitexture": the parent sprite loads this copy from the same, already changed, images.
        if (name.endsWith(".spriteSingle")) return strip;
        return process(name, strip, upload);
    }

    /**
     * Changes a stand-alone texture such as "minecraft:textures/entity/pig/pig.png", where only a plain SimpleTexture can repaint in place.
     *
     * @return the input changed in place, or a changed ARGB copy of it
     */
    public static BufferedImage processTexture(SimpleTexture texture, ResourceLocation location, BufferedImage image) {
        bootstrap();
        if (image == null || Albedo.repainting()) return image;
        ReloadScreen.load();
        TextureStore.Upload upload;
        if (texture.getClass() == SimpleTexture.class) upload = new SimpleTextureUpload(texture, location);
        else if (texture instanceof ThreadDownloadImageData) upload = null; // a skin placeholder must not replace the default skin
        else upload = TextureStore.RELOAD_ONLY; // a texture type from a different mod
        return process(String.valueOf(location), image, upload);
    }

    /** SkinManager.loadSkin starts with a texture type ("SKIN", "CAPE"), or ends when {@code type} is null. */
    public static void skinLoading(String type) {
        if (type == null) LOADING_SKIN.remove();
        else LOADING_SKIN.set(type.toLowerCase(Locale.ROOT) + "s");
    }

    /** Tags a downloaded texture that SkinManager.loadSkin registers, and keeps the tag when a reload loads it again. */
    public static void loadingTexture(ResourceLocation location, ITextureObject texture) {
        String folder = LOADING_SKIN.get();
        if (folder == null || !(texture instanceof ThreadDownloadImageData)) return;
        LOADING_SKIN.remove(); // one texture for each loadSkin call
        String id = location.toString(); // "<domain>:skins/<hash>" for skins and capes alike
        String name = id.substring(0, id.indexOf(':') + 1) + folder + id.substring(id.indexOf('/'));
        ((DownloadedTextureUpload.State) texture).albedo$tag(location, name);
    }

    /** Drops the kept original of a downloaded texture that the game deletes. */
    public static void deletingTexture(ResourceLocation location) {
        ITextureObject texture = Minecraft.getMinecraft().getTextureManager().getTexture(location);
        if (texture instanceof ThreadDownloadImageData) {
            String name = ((DownloadedTextureUpload.State) texture).albedo$name();
            if (name != null) Albedo.forgetTexture(name);
        }
    }

    /**
     * Changes a downloaded skin or cape, on the client thread or a download thread.
     *
     * @return the image to keep, which the texture uploads later
     */
    public static BufferedImage processDownloaded(ThreadDownloadImageData texture, BufferedImage image) {
        DownloadedTextureUpload.State state = (DownloadedTextureUpload.State) texture;
        if (image == null || state.albedo$name() == null || Albedo.repainting()) return image;
        bootstrap();
        return process(state.albedo$name(), image, new DownloadedTextureUpload(texture, state.albedo$location()));
    }

    private static BufferedImage process(String location, BufferedImage image, TextureStore.Upload upload) {
        BufferedImage argb = BufferedImagePixels.toArgb(image);
        return Albedo.processTexture(location, new BufferedImagePixels(argb), upload) ? argb : image;
    }

    public static void reloadBegin(int listeners) {
        ReloadScreen.begin(listeners);
    }

    public static void reloadStep() {
        ReloadScreen.step();
    }

    /** Removes the face shade that every model has from a quad when shading is off, so that a shading change needs no model reload. */
    static int[] quad(int[] vertexData) {
        return shading() ? vertexData : FaceShade.remove(vertexData);
    }

    /** The same for Forge's block lighting pipeline: one vertex color, 0..1, before tint and smooth lighting. */
    public static void vertexColor(float[] rgba) {
        if (!shading()) FaceShade.remove(rgba);
    }

    public static int blockTint(int original) {
        return Albedo.currentPolicy().removeTint(Category.BLOCKS) ? 0xFFFFFF : original;
    }

    /** OptiFine CustomColors: -1 means "no custom color" and stays -1. */
    public static int optiFineBlockTint(int original) {
        return original == -1 ? -1 : blockTint(original);
    }

    public static boolean removeFluidTint() {
        return Albedo.currentPolicy().removeTint(Category.FLUIDS);
    }

    public static int fluidTint(int original) {
        return removeFluidTint() ? 0xFFFFFF : original;
    }

    /** OptiFine's fluid renderer reads the face shade of FaceBakery each frame. */
    public static float faceBrightness(float original) {
        return shading() ? original : 1.0f;
    }

    /** Changes the "grass" or "foliage" colormap in the array that the game keeps, so that a repaint can change it again. */
    public static void colormap(String name, int[] pixels) {
        bootstrap();
        Albedo.processColormap(name, pixels);
    }

    public static float gamma(float setting) {
        return (float) Albedo.currentPolicy().gamma(setting);
    }

    public static boolean shading() {
        return Albedo.currentPolicy().shading();
    }

    public static boolean shadows() {
        return Albedo.currentPolicy().shadows();
    }

    /** Whether the armor layer skips an entity: a player, while the profile hides player armor. */
    public static boolean hideArmor(boolean player) {
        return player && !Albedo.currentPolicy().playerArmor();
    }

    /** Whether the elytra layer of 1.12.2 skips an entity: a player, while the profile hides player armor. */
    public static boolean hideElytra(boolean player) {
        ELYTRA.run();
        return hideArmor(player);
    }

    /** Whether the cape layer skips a player, while the profile hides player capes. */
    public static boolean hideCape() {
        CAPE.run();
        return !Albedo.currentPolicy().playerCape();
    }

    public static boolean overridesSky() {
        return Albedo.currentPolicy().overridesSky();
    }

    /** @return {r, g, b} in 0..1 or null when the sky is not overridden */
    public static float[] skyRgb() {
        return Albedo.currentPolicy().skyRgb();
    }
}
