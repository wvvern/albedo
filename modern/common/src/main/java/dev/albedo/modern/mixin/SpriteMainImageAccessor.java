package dev.albedo.modern.mixin;

//? if <1.19.3 {
/*import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The image of a sprite and its mip levels, on the versions before SpriteContents.
@Mixin(TextureAtlasSprite.class)
public interface SpriteMainImageAccessor {
    @Accessor("mainImage")
    NativeImage[] albedo$mainImage();

    @Accessor("x")
    int albedo$x();

    @Accessor("y")
    int albedo$y();
}
*///?}
