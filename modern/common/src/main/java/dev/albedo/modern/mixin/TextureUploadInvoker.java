package dev.albedo.modern.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The step that puts a decoded image into the GPU texture of a stand-alone texture. */
//? if >=1.21.4 {
@Mixin(net.minecraft.client.renderer.texture.ReloadableTexture.class)
public interface TextureUploadInvoker {
    @Invoker("doLoad")
    void albedo$doLoad(NativeImage image);
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.texture.SimpleTexture.class)
public interface TextureUploadInvoker {
    @Invoker("doLoad")
    void albedo$doLoad(NativeImage image, boolean blur, boolean clamp);
}
*///?}
