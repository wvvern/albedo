package dev.albedo.modern;

//? if >=1.21.6 {
import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.sky.SkyImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** The texture of the baked sky image for {@code PrecompiledSky} and {@code PearlSky}. */
final class BakedSkyTexture {
    private static DynamicTexture texture;
    private static long uploaded = -1;

    private BakedSkyTexture() {}

    /** The texture as {@link #upload} left it, or null before the first image. */
    static DynamicTexture current() {
        return texture;
    }

    /** Sends a new image to the texture, of a fixed size, and returns the texture; the render thread calls this. */
    static DynamicTexture upload(SkyImage img) {
        if (texture == null || texture.getPixels().getWidth() != img.width() || texture.getPixels().getHeight() != img.height()) {
            if (texture != null) texture.close();
            texture = new DynamicTexture(() -> "Albedo sky", new NativeImage(img.width(), img.height(), false));
            uploaded = -1;
        }
        if (uploaded != img.serial()) {
            new NativeImagePixels(texture.getPixels()).setAll(img.argb());
            texture.upload();
            uploaded = img.serial();
        }
        return texture;
    }
}
//?}
