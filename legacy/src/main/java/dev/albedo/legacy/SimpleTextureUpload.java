package dev.albedo.legacy;

import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/**
 * Writes new pixels into the GL texture of a SimpleTexture (entities, sky, the particle sheet), with the blur and
 * clamp settings of its texture metadata. The size does not change, so the texture is not allocated again.
 */
public final class SimpleTextureUpload implements TextureStore.Upload {
    /** The GL state of a SimpleTexture. SimpleTextureMixin implements it. */
    public interface State {
        /** -1 when the texture has no GL texture. */
        int albedo$glTextureId();

        boolean albedo$blur();

        boolean albedo$clamp();
    }

    /** For a subclass that can change its texture in a different way: a change to it reloads resources. */
    static final TextureStore.Upload UNKNOWN_SUBCLASS = new TextureStore.Upload() {
        @Override
        public boolean upload(PixelBuffer pixels) {
            throw new UnsupportedOperationException("not a plain SimpleTexture");
        }
    };

    private final SimpleTexture texture;
    private final ResourceLocation location;

    SimpleTextureUpload(SimpleTexture texture, ResourceLocation location) {
        this.texture = texture;
        this.location = location;
    }

    @Override
    public boolean upload(PixelBuffer pixels) {
        State state = (State) texture;
        if (Minecraft.getMinecraft().getTextureManager().getTexture(location) != texture || state.albedo$glTextureId() == -1) {
            return false; // a reload or a resource pack change replaced it
        }
        if (OptiFine.shaders()) throw new IllegalStateException("OptiFine shaders use their own texture upload");
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        TextureUtil.uploadTextureImageSub(state.albedo$glTextureId(), BufferedImagePixels.toImage(pixels), 0, 0,
                state.albedo$blur(), state.albedo$clamp());
        return true;
    }
}
