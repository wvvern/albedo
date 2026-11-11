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

    /** The column of the first pixel of the sprite in its atlas. */
    static int x(TextureAtlasSprite sprite) {
        return sprite.getX();
    }

    /** The row of the first pixel of the sprite in its atlas. */
    static int y(TextureAtlasSprite sprite) {
        return sprite.getY();
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

    /** Makes the mip levels of {@code sprite} again from the image, the same way as at load. */
    void remip(TextureAtlasSprite sprite) {
        SpriteContents current = sprite.contents();
        SpriteContentsAccessor access = (SpriteContentsAccessor) current;
        NativeImage[] before = access.albedo$byMipLevel();
        access.albedo$setByMipLevel(new NativeImage[]{access.albedo$originalImage()});
        current.increaseMipLevel(before.length - 1);
        for (int level = 1; level < before.length; level++) before[level].close();
    }
}
//?} else {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.modern.mixin.SpriteMainImageAccessor;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/^*
 * The name and the image of one atlas sprite on the versions where a sprite keeps its image and its mip levels in an
 * array of its own, so that the callers name no sprite class of one version.
 ^/
public final class SpriteAccess {
    private final ResourceLocation name;
    private final NativeImage image;
    private final int width;
    private final int height;

    /^* @param image the image that the sprite is made from, which stays its level 0 ^/
    public SpriteAccess(ResourceLocation name, NativeImage image, int width, int height) {
        this.name = name;
        this.image = image;
        this.width = width;
        this.height = height;
    }

    /^* The access to a sprite that an atlas holds. ^/
    static SpriteAccess of(TextureAtlasSprite sprite) {
        return new SpriteAccess(sprite.getName(), levels(sprite)[0], sprite.getWidth(), sprite.getHeight());
    }

    /^* The column of the first pixel of the sprite in its atlas. ^/
    static int x(TextureAtlasSprite sprite) {
        return ((SpriteMainImageAccessor) sprite).albedo$x();
    }

    /^* The row of the first pixel of the sprite in its atlas. ^/
    static int y(TextureAtlasSprite sprite) {
        return ((SpriteMainImageAccessor) sprite).albedo$y();
    }

    /^* The name of the sprite in its atlas, for example {@code minecraft:block/stone}. ^/
    String name() {
        return String.valueOf(name);
    }

    /^* The width of one frame. ^/
    int width() {
        return width;
    }

    /^* The height of one frame. ^/
    int height() {
        return height;
    }

    /^* The full image with each animation frame, which the mip levels come from. ^/
    NativeImage image() {
        return image;
    }

    /^* The sprite of an atlas that still has this image, or null after a reload gave the atlas a new sprite. ^/
    TextureAtlasSprite in(TextureAtlas atlas) {
        TextureAtlasSprite sprite = atlas.getSprite(name);
        return levels(sprite)[0] == image ? sprite : null;
    }

    /^* Makes the mip levels of {@code sprite} again from the image, the same way as at load. ^/
    void remip(TextureAtlasSprite sprite) {
        NativeImage[] levels = levels(sprite);
        NativeImage[] fresh = MipmapGenerator.generateMipLevels(image, levels.length - 1);
        for (int level = 1; level < levels.length; level++) {
            levels[level].close();
            levels[level] = fresh[level];
        }
    }

    private static NativeImage[] levels(TextureAtlasSprite sprite) {
        return ((SpriteMainImageAccessor) sprite).albedo$mainImage();
    }
}
*///?}
