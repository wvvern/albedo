package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;

/** What the 1.8.9 mixins call: thin translations from 1.8.9 types to the core. */
public final class LegacyHooks {
    private LegacyHooks() {}

    public static void bootstrap() {
        if (Albedo.get() == null) Albedo.init(new LegacyPlatform());
    }

    public static void clientTick() {
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
        Albedo.processColormap("minecraft:textures/colormap/" + name + ".png", pixels);
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
}
