package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import dev.albedo.modern.mixin.SpriteContentsAccessor;
import dev.albedo.modern.mixin.TextureManagerAccessor;
import dev.albedo.modern.mixin.TextureUploadInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=1.21.11 {
import dev.albedo.modern.mixin.TextureAtlasAccessor;
import org.lwjgl.system.MemoryUtil;

import java.util.LinkedHashMap;
import java.util.Map;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * Puts repainted pixels back into a loaded texture and its GPU copy on the render thread, so that a settings change
 * needs no resource reload.
 */
final class TextureUploads {
    private TextureUploads() {}

    /** A sprite with new pixels, which {@link #flush} puts into the sprite and its atlas. */
    private static final class Pending {
        final TextureAtlas atlas;
        final TextureAtlasSprite sprite;
        final PixelBuffer pixels;
        /** Border of copied edge pixels around the sprite in the atlas, at mip level 0. */
        final int padding;
        /** Still sprites: each mip level with its padding, as it goes into the atlas. */
        NativeImage[] padded;

        Pending(TextureAtlas atlas, TextureAtlasSprite sprite, PixelBuffer pixels, int padding) {
            this.atlas = atlas;
            this.sprite = sprite;
            this.pixels = pixels;
            this.padding = padding;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Uploads into an atlas sprite only while the game still uses it, because a reload (a resource pack change or F3+T)
     * replaces sprites and textures and the load hooks then record new uploads.
     *
     * @param atlasId the location of the atlas texture
     */
    static TextureStore.Upload sprite(Object atlasId, SpriteContents contents) {
        return pixels -> {
            if (!(registered(atlasId) instanceof TextureAtlas atlas)) return false;
            TextureAtlasSprite sprite = atlas.getSprite(contents.name());
            if (sprite.contents() != contents) return false;
            NativeImage original = ((SpriteContentsAccessor) contents).albedo$originalImage();
            if (original.getWidth() != pixels.width() || original.getHeight() != pixels.height()) return false;
            //? if >=1.21.11 {
            // u0 = (x + padding) / atlas width.
            int padding = Math.round(sprite.getU0() * atlas.getTexture().getWidth(0)) - sprite.getX();
            //?} else {
            /*int padding = 0;
            *///?}
            PENDING.add(new Pending(atlas, sprite, pixels, padding));
            return true;
        };
    }

    /** Puts the repainted sprites into their atlases, and must run after each repaint, also when an upload failed. */
    static void flush() {
        try {
            PENDING.parallelStream().forEach(p -> {
                repaint(p.sprite.contents(), p.pixels);
                //? if >=1.21.11 {
                if (!p.sprite.contents().isAnimated()) p.padded = padded(p.sprite.contents(), p.padding);
                //?}
            });
            //? if >=1.21.11 {
            var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
            Map<TextureAtlas, List<TextureAtlasSprite>> animated = new LinkedHashMap<>();
            for (Pending p : PENDING) {
                if (p.padded == null) {
                    animated.computeIfAbsent(p.atlas, a -> new ArrayList<>()).add(p.sprite);
                    continue;
                }
                for (int level = 0; level < p.padded.length; level++) {
                    NativeImage image = p.padded[level];
                    int x = p.sprite.getX() >> level, y = p.sprite.getY() >> level;
                    //? if >=26.1 {
                    encoder.writeToTexture(p.atlas.getTexture(), image, level, 0, x, y);
                    //?} else {
                    /*encoder.writeToTexture(p.atlas.getTexture(), image, level, 0, x, y, image.getWidth(), image.getHeight(), 0, 0);
                    *///?}
                }
            }
            animated.forEach((atlas, sprites) -> replaceAnimations((TextureAtlasAccessor) atlas, sprites));
            //?} else {
            /*// Animated sprites upload their next frame from byMipLevel, so they animate with the new pixels.
            for (Pending p : PENDING) {
                p.atlas.bind();
                p.sprite.uploadFirstFrame();
            }
            *///?}
        } finally {
            for (Pending p : PENDING) {
                if (p.padded != null) for (NativeImage image : p.padded) image.close();
            }
            PENDING.clear();
        }
    }

    /** Writes the full image (every animation frame), then makes the mip levels from it, the same way as at load. */
    private static void repaint(SpriteContents contents, PixelBuffer pixels) {
        SpriteContentsAccessor access = (SpriteContentsAccessor) contents;
        write(pixels, access.albedo$originalImage());
        NativeImage[] before = access.albedo$byMipLevel();
        access.albedo$setByMipLevel(new NativeImage[]{access.albedo$originalImage()});
        contents.increaseMipLevel(before.length - 1);
        for (int level = 1; level < before.length; level++) before[level].close();
    }

    //? if >=1.21.11 {
    /**
     * Each mip level of a still sprite as the atlas draws it, with a border of its edge pixels (nearest filter, clamp to
     * edge) {@code padding >> level} wide, made without a draw.
     */
    private static NativeImage[] padded(SpriteContents contents, int padding) {
        NativeImage[] mips = ((SpriteContentsAccessor) contents).albedo$byMipLevel();
        NativeImage[] out = new NativeImage[mips.length];
        for (int level = 0; level < mips.length; level++) {
            NativeImage source = mips[level];
            int border = padding >> level;
            int width = (contents.width() + 2 * padding) >> level, height = (contents.height() + 2 * padding) >> level;
            int spriteWidth = contents.width() >> level, maxY = (contents.height() >> level) - 1;
            NativeImage image = new NativeImage(width, height, false);
            // Native copies only (4 bytes for each pixel): each sprite row with its edge pixels repeated to the sides,
            // then the edge rows repeated above and below.
            long sourceRows = source.getPointer(), rows = image.getPointer(), rowBytes = 4L * width;
            for (int y = 0; y <= maxY; y++) {
                long from = sourceRows + 4L * source.getWidth() * y;
                long to = rows + rowBytes * (y + border);
                MemoryUtil.memCopy(from, to + 4L * border, 4L * spriteWidth);
                repeat(to, MemoryUtil.memGetInt(from), border);
                repeat(to + 4L * (border + spriteWidth), MemoryUtil.memGetInt(from + 4L * (spriteWidth - 1)), width - border - spriteWidth);
            }
            for (int y = 0; y < height; y++) {
                int inside = Math.clamp(y, border, border + maxY);
                if (inside != y) MemoryUtil.memCopy(rows + rowBytes * inside, rows + rowBytes * y, rowBytes);
            }
            out[level] = image;
        }
        return out;
    }

    /** Writes {@code pixel} {@code count} times from {@code address}, with a few doubling copies. */
    private static void repeat(long address, int pixel, int count) {
        if (count <= 0) return;
        MemoryUtil.memPutInt(address, pixel);
        for (int done = 1; done < count; done *= 2) MemoryUtil.memCopy(address, address + 4L * done, 4L * Math.min(done, count - done));
    }

    /**
     * Replaces the animation state of each repainted animated sprite, which keeps each frame in a texture of its own,
     * with one made from the new frames, then draws the first frames.
     */
    private static void replaceAnimations(TextureAtlasAccessor atlas, List<TextureAtlasSprite> sprites) {
        List<SpriteContents.AnimationState> states = new ArrayList<>(atlas.albedo$animationStates());
        int index = 0; // the atlas made the states in the order of its animated sprites
        for (TextureAtlasSprite sprite : atlas.albedo$sprites()) {
            if (!sprite.contents().isAnimated()) continue;
            if (sprites.contains(sprite)) {
                SpriteContents.AnimationState old = states.get(index);
                var ubo = old.getDrawUbo(0);
                var block = ubo.buffer().slice(ubo.offset(), ubo.length() * atlas.albedo$mipLevelCount());
                states.set(index, sprite.contents().createAnimationState(block, (int) ubo.length()));
                old.close();
            }
            index++;
        }
        atlas.albedo$setAnimationStates(List.copyOf(states));
        atlas.albedo$uploadAnimationFrames();
    }
    //?}

    //? if >=1.21.4 {
    /** A stand-alone texture, loaded with TextureContents.load. */
    static TextureStore.Upload texture(Object location) {
        return pixels -> {
            if (!(registered(location) instanceof net.minecraft.client.renderer.texture.ReloadableTexture texture)
                    || !texture.resourceId().equals(location)) return false;
            try (NativeImage image = new NativeImage(pixels.width(), pixels.height(), false)) {
                write(pixels, image);
                // Makes a new GPU texture (with mip levels where the texture has them), as a reload does.
                ((TextureUploadInvoker) texture).albedo$doLoad(image);
            }
            return true;
        };
    }
    //?} else {
    /*// A stand-alone texture. The texture object loaded it with these filter settings.
    static TextureStore.Upload texture(Object location, net.minecraft.client.renderer.texture.SimpleTexture texture,
                                       boolean blur, boolean clamp) {
        return pixels -> {
            if (registered(location) != texture) return false;
            NativeImage image = new NativeImage(pixels.width(), pixels.height(), false);
            write(pixels, image);
            // Uploads and closes the image.
            ((TextureUploadInvoker) texture).albedo$doLoad(image, blur, clamp);
            return true;
        };
    }
    *///?}

    /** The texture registered at {@code location} now, or null, without making one as TextureManager.getTexture does. */
    private static AbstractTexture registered(Object location) {
        return ((TextureManagerAccessor) Minecraft.getInstance().getTextureManager()).albedo$byPath().get(location);
    }

    private static void write(PixelBuffer pixels, NativeImage image) {
        //? if >=1.21.11 {
        if (pixels instanceof PixelBuffer.Heap heap && image.format() == NativeImage.Format.RGBA && image.getWidth() == heap.width()
                && image.getHeight() == heap.height()) {
            // One native copy, after a swap to ABGR, which is how the image keeps each pixel.
            int[] argb = heap.argb();
            int[] abgr = new int[argb.length];
            for (int i = 0; i < argb.length; i++) {
                int c = argb[i];
                abgr[i] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
            }
            MemoryUtil.memIntBuffer(image.getPointer(), abgr.length).put(abgr);
            return;
        }
        //?}
        NativeImagePixels out = new NativeImagePixels(image);
        for (int y = 0; y < pixels.height(); y++) {
            for (int x = 0; x < pixels.width(); x++) out.setArgb(x, y, pixels.getArgb(x, y));
        }
    }
}
