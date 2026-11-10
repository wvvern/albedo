package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/** What the 1.8.9 mixins call: thin translations from 1.8.9 types to the core. */
public final class LegacyHooks {
    /** The host of the last connection to a server, which stays until the next connection. */
    private static volatile String connectedHost;

    private LegacyHooks() {}

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
        Albedo sc = Albedo.get();
        if (sc != null) sc.clientTick(Minecraft.getMinecraft().currentScreen != null);
    }

    /** Changes the animation strip {@code images[0]} of a block atlas sprite before loadSprite makes frames and mip levels from it. */
    public static void processSprite(TextureAtlasSprite sprite, BufferedImage[] images, AnimationMetadataSection meta) {
        bootstrap();
        // A repaint calls loadSprite with pixels that are already done: nothing to keep or change.
        if (Albedo.repainting() || images == null || images.length == 0 || images[0] == null) return;
        ReloadScreen.load();
        String name = sprite.getIconName();
        // OptiFine "Multitexture": the parent sprite loads this copy from the same, already changed, images.
        if (name.endsWith(".spriteSingle")) return;
        images[0] = process(name, images[0], new SpriteUpload(sprite, images, meta));
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

    /** The skin cache folder of SkinManager, which SkinManagerMixin gives. */
    public interface SkinCache {
        File albedo$skinCacheDir();
    }

    /** The folder of the texture that SkinManager.loadSkin loads on this thread now ("skins" or "capes"), or null. */
    private static final ThreadLocal<String> LOADING_SKIN = new ThreadLocal<String>();

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
        String path = location.getResourcePath(); // "skins/<hash>" for skins and capes alike
        String name = location.getResourceDomain() + ":" + folder + path.substring(path.indexOf('/'));
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

    /** The start of a resource reload, with its number of reload listeners. */
    public static void reloadBegin(int listeners) {
        ReloadScreen.begin(listeners);
    }

    /** The next reload listener starts. */
    public static void reloadStep() {
        ReloadScreen.step();
    }

    /** Removes the face shade that every model has from a quad when shading is off, so that a shading change needs no model reload. */
    public static void addQuad(WorldRenderer renderer, int[] vertexData) {
        renderer.addVertexData(shading() ? vertexData : FaceShade.remove(vertexData));
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

    public static boolean overridesSky() {
        return Albedo.currentPolicy().overridesSky();
    }

    /** @return {r, g, b} in 0..1 or null when the sky is not overridden */
    public static float[] skyRgb() {
        return Albedo.currentPolicy().skyRgb();
    }

    /** True when the fog code of the game gives the camera the fog of water, lava or blindness, which hides the sky. */
    public static boolean specialFog(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.entity.Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) return false;
        net.minecraft.block.material.Material material =
                net.minecraft.client.renderer.ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, view, partialTicks).getMaterial();
        if (material == net.minecraft.block.material.Material.water || material == net.minecraft.block.material.Material.lava) return true;
        return view instanceof net.minecraft.entity.EntityLivingBase
                && ((net.minecraft.entity.EntityLivingBase) view).isPotionActive(net.minecraft.potion.Potion.blindness);
    }
}
