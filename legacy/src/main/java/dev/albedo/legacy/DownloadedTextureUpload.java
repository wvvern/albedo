package dev.albedo.legacy;

import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;

/**
 * Writes new pixels into a downloaded skin or cape: into its image while it waits for its first upload, else into its
 * GL texture.
 */
public final class DownloadedTextureUpload implements TextureStore.Upload {
    /** The download state of a ThreadDownloadImageData, which ThreadDownloadImageDataMixin implements. */
    public interface State {
        /** Gives the texture the location that the texture manager keeps it at and the name that the core knows it by. */
        void albedo$tag(ResourceLocation location, String name);

        /** The location that the texture manager keeps the texture at, or null for a texture that SkinManager did not load. */
        ResourceLocation albedo$location();

        /** The core name, for example "minecraft:capes/<hash>", or null. */
        String albedo$name();

        /** True after the first upload of the downloaded image to the GL texture. */
        boolean albedo$uploaded();

        /** Replaces the downloaded image that the first upload sends. */
        void albedo$setImage(BufferedImage image);
    }

    private final ThreadDownloadImageData texture;
    private final ResourceLocation location;

    DownloadedTextureUpload(ThreadDownloadImageData texture, ResourceLocation location) {
        this.texture = texture;
        this.location = location;
    }

    @Override
    public boolean inPlace() {
        return true;
    }

    @Override
    public boolean upload(PixelBuffer pixels) {
        if (Minecraft.getMinecraft().getTextureManager().getTexture(location) != texture) return false; // deleted
        // A reload cannot change a downloaded skin, so with shaders the store forgets it and it keeps its look.
        if (OptiFine.shaders()) return false;
        State state = (State) texture;
        BufferedImage image = BufferedImagePixels.toImage(pixels);
        if (!state.albedo$uploaded()) {
            state.albedo$setImage(image);
            return true;
        }
        int id = ((SimpleTextureUpload.State) texture).albedo$glTextureId();
        if (id == -1) return false;
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        TextureUtil.uploadTextureImageSub(id, image, 0, 0, false, false);
        return true;
    }
}
