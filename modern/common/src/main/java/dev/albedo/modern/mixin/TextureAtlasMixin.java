package dev.albedo.modern.mixin;

//? if <1.19.3 {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.modern.ModernHooks;
import dev.albedo.modern.SpriteAccess;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Every atlas sprite is made here from its loaded image, before the mip levels exist, on the versions before SpriteLoader.
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    @ModifyArg(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Info;IIIII)"
            + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;<init>(Lnet/minecraft/client/renderer/texture/TextureAtlas;"
                    + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Info;IIIIILcom/mojang/blaze3d/platform/NativeImage;)V"),
            index = 7)
    private NativeImage albedo$flatten(TextureAtlas atlas, TextureAtlasSprite.Info info, int mipLevel, int atlasWidth, int atlasHeight, int x, int y,
                                       NativeImage image) {
        ModernHooks.processSprite(atlas.location(), new SpriteAccess(info.name(), image, info.width(), info.height()));
        return image;
    }
}
*///?}
