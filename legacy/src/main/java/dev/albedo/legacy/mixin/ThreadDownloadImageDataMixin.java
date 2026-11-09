package dev.albedo.legacy.mixin;

import dev.albedo.legacy.DownloadedTextureUpload;
import dev.albedo.legacy.LegacyHooks;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.awt.image.BufferedImage;

/**
 * Downloaded skins and capes, which get their image on the client thread (from the skin cache) or on a download
 * thread, after the game converts an old 64 x 32 skin.
 */
@Mixin(ThreadDownloadImageData.class)
public abstract class ThreadDownloadImageDataMixin implements DownloadedTextureUpload.State {
    @Shadow
    private BufferedImage bufferedImage;

    @Shadow
    private boolean textureUploaded;

    private ResourceLocation albedo$location;
    private String albedo$name;

    @ModifyVariable(method = "setBufferedImage", at = @At("HEAD"), argsOnly = true)
    private BufferedImage albedo$paint(BufferedImage image) {
        return LegacyHooks.processDownloaded((ThreadDownloadImageData) (Object) this, image);
    }

    @Override
    public void albedo$tag(ResourceLocation location, String name) {
        albedo$location = location;
        albedo$name = name;
    }

    @Override
    public ResourceLocation albedo$location() {
        return albedo$location;
    }

    @Override
    public String albedo$name() {
        return albedo$name;
    }

    @Override
    public boolean albedo$uploaded() {
        return textureUploaded;
    }

    @Override
    public void albedo$setImage(BufferedImage image) {
        bufferedImage = image;
    }
}
