package dev.blockpainter.modern.mixin;

import dev.blockpainter.modern.ModernHooks;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stand-alone (non-atlas) textures: entities, rain/snow, end sky, and on 1.21.1 also sun, moon and clouds. */
//? if >=1.21.4 {
@Mixin(net.minecraft.client.renderer.texture.TextureContents.class)
public abstract class TextureLoadMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void blockpainter$flatten(ResourceManager manager,
                                            //? if >=1.21.11 {
                                            net.minecraft.resources.Identifier location,
                                            //?} else {
                                            /*net.minecraft.resources.ResourceLocation location,
                                            *///?}
                                            CallbackInfoReturnable<net.minecraft.client.renderer.texture.TextureContents> cir) {
        ModernHooks.processImage(String.valueOf(location), cir.getReturnValue().image());
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.texture.SimpleTexture.class)
public abstract class TextureLoadMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    protected net.minecraft.resources.ResourceLocation location;

    // SimpleTexture.TextureImage is protected, so hook the private upload step that receives the image instead.
    @Inject(method = "doLoad", at = @At("HEAD"))
    private void blockpainter$flatten(com.mojang.blaze3d.platform.NativeImage image, boolean blur, boolean clamp,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ModernHooks.processImage(String.valueOf(location), image);
    }
}
*///?}
