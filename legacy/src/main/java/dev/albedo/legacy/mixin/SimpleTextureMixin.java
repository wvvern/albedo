package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
import dev.albedo.legacy.SimpleTextureUpload;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stand-alone textures (entities, sky, the particle sheet), with the blur and clamp settings of the upload kept so
 * that a repaint (SimpleTextureUpload) writes the pixels with the same settings.
 */
@Mixin(SimpleTexture.class)
public abstract class SimpleTextureMixin extends AbstractTexture implements SimpleTextureUpload.State {
    @Shadow
    @Final
    protected ResourceLocation textureLocation;

    private boolean albedo$blur;
    private boolean albedo$clamp;

    @Redirect(method = "loadTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;readBufferedImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage albedo$flatten(InputStream in) throws IOException {
        return LegacyHooks.processTexture((SimpleTexture) (Object) this, textureLocation, TextureUtil.readBufferedImage(in));
    }

    @Redirect(method = "loadTexture", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureImageAllocate(ILjava/awt/image/BufferedImage;ZZ)I"))
    private int albedo$upload(int textureId, BufferedImage image, boolean blur, boolean clamp) {
        LegacyHooks.textureUpload();
        albedo$blur = blur;
        albedo$clamp = clamp;
        return TextureUtil.uploadTextureImageAllocate(textureId, image, blur, clamp);
    }

    @Override
    public int albedo$glTextureId() {
        return glTextureId;
    }

    @Override
    public boolean albedo$blur() {
        return albedo$blur;
    }

    @Override
    public boolean albedo$clamp() {
        return albedo$clamp;
    }
}
