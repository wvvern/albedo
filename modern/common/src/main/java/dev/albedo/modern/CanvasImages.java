package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.ui.CanvasImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The textures of the images that the editor draws, one for each id and size, which stay until the screen closes because
 * the game draws the GUI after this frame's calls.
 */
final class CanvasImages {
    private final Map<String, DynamicTexture> textures = new HashMap<String, DynamicTexture>();
    private final Map<String, Long> serials = new HashMap<String, Long>();

    //? if >=1.21.11 {
    static Identifier id(String key) {
        return ModIds.id("gui/" + key);
    }
    //?} else {
    /*static ResourceLocation id(String key) {
        return ModIds.id("gui/" + key);
    }
    *///?}

    /** Registers the texture of {@code id} with the pixels of {@code img}, and returns its key for {@link #id}. */
    String prepare(String id, CanvasImage img) {
        String key = (id + "_" + img.width() + "x" + img.height()).toLowerCase(Locale.ROOT);
        DynamicTexture texture = textures.get(key);
        if (texture == null) {
            NativeImage pixels = new NativeImage(img.width(), img.height(), false);
            //? if >=1.21.5 {
            texture = new DynamicTexture(() -> "Albedo " + key, pixels);
            //?} else {
            /*texture = new DynamicTexture(pixels);
            *///?}
            textures.put(key, texture);
            Minecraft.getInstance().getTextureManager().register(id(key), texture);
        }
        Long serial = serials.get(key);
        if (serial == null || serial != img.serial()) {
            NativeImagePixels out = new NativeImagePixels(texture.getPixels());
            int[] argb = img.argb();
            for (int py = 0; py < img.height(); py++) {
                for (int px = 0; px < img.width(); px++) out.setArgb(px, py, argb[py * img.width() + px] | 0xFF000000);
            }
            texture.upload();
            serials.put(key, img.serial());
        }
        return key;
    }

    void close() {
        for (String key : textures.keySet()) Minecraft.getInstance().getTextureManager().release(id(key));
        textures.clear();
        serials.clear();
    }
}
