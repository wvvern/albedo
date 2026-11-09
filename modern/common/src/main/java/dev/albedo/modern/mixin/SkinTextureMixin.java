package dev.albedo.modern.mixin;

import dev.albedo.modern.ModernHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Downloaded skins, capes and elytra: from 1.21.4 a DynamicTexture that TextureManager.register keeps on the client
 * thread, before 1.21.4 the image that HttpTexture decodes.
 */
//? if >=1.21.4 {
@Mixin(net.minecraft.client.renderer.texture.TextureManager.class)
public abstract class SkinTextureMixin {
    @Inject(method = "register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
            at = @At("RETURN"))
    private void albedo$registered(net.minecraft.resources.Identifier id, net.minecraft.client.renderer.texture.AbstractTexture texture,
                                   org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ModernHooks.registered(id, texture);
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.texture.HttpTexture.class)
public abstract class SkinTextureMixin implements dev.albedo.modern.DownloadedSkin {
    private Object albedo$id;

    @org.spongepowered.asm.mixin.Shadow
    private void upload(com.mojang.blaze3d.platform.NativeImage image) {}

    @Inject(method = "load(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;", at = @At("RETURN"))
    private void albedo$loaded(java.io.InputStream in,
                               org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.mojang.blaze3d.platform.NativeImage> cir) {
        if (cir.getReturnValue() != null) ModernHooks.processDownloaded(albedo$id, cir.getReturnValue(), this);
    }

    @Override
    public void albedo$setId(Object id) {
        albedo$id = id;
    }

    @Override
    public void albedo$upload(com.mojang.blaze3d.platform.NativeImage image) {
        upload(image);
    }
}
*///?}
