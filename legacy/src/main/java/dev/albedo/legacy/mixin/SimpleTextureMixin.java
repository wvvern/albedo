package dev.albedo.legacy.mixin;

import dev.albedo.legacy.LegacyHooks;
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

/** Stand-alone textures: entities, sky (sun, moon, clouds, rain, snow), the particle sheet. */
@Mixin(SimpleTexture.class)
public abstract class SimpleTextureMixin {
    @Shadow
    @Final
    protected ResourceLocation textureLocation;

    @Redirect(method = "loadTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;readBufferedImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage albedo$flatten(InputStream in) throws IOException {
        return LegacyHooks.processImage(String.valueOf(textureLocation), TextureUtil.readBufferedImage(in));
    }
}
