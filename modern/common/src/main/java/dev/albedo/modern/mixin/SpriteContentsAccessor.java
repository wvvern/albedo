package dev.albedo.modern.mixin;

//? if >=1.19.3 {
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("originalImage")
    NativeImage albedo$originalImage();

    @Accessor("byMipLevel")
    NativeImage[] albedo$byMipLevel();

    @Accessor("byMipLevel")
    void albedo$setByMipLevel(NativeImage[] images);
}
//?}
