package dev.albedo.modern;

//? if >=1.19.3 {
import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.modern.mixin.SpriteContentsAccessor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The name and the image of one atlas sprite on the versions that keep them in a {@code SpriteContents}, so that the
 * callers name no sprite class of one version.
 */
public final class SpriteAccess {
    private final SpriteContents contents;

    public SpriteAccess(SpriteContents contents) {
        this.contents = contents;
    }

    /** The access to a sprite that an atlas holds. */
    static SpriteAccess of(TextureAtlasSprite sprite) {
        return new SpriteAccess(sprite.contents());
    }

    /** The name of the sprite in its atlas, for example {@code minecraft:block/stone}. */
    String name() {
        return String.valueOf(contents.name());
    }

    /** The width of one frame. */
    int width() {
        return contents.width();
    }

    /** The height of one frame. */
    int height() {
        return contents.height();
    }

    /** The full image with each animation frame, which the mip levels come from. */
    NativeImage image() {
        return ((SpriteContentsAccessor) contents).albedo$originalImage();
    }

    /** The sprite of an atlas that still has this image, or null after a reload gave the atlas a new sprite. */
    TextureAtlasSprite in(TextureAtlas atlas) {
        TextureAtlasSprite sprite = atlas.getSprite(contents.name());
        return sprite.contents() == contents ? sprite : null;
    }

    /** Makes the mip levels again from the image, the same way as at load. */
    void remip() {
        SpriteContentsAccessor access = (SpriteContentsAccessor) contents;
        NativeImage[] before = access.albedo$byMipLevel();
        access.albedo$setByMipLevel(new NativeImage[]{access.albedo$originalImage()});
        contents.increaseMipLevel(before.length - 1);
        for (int level = 1; level < before.length; level++) before[level].close();
    }
}
//?}
