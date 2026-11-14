package dev.albedo.modern;

//? if >=1.21.11 {
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.albedo.modern.mixin.SpriteContentsAccessor;
import dev.albedo.modern.mixin.TextureAtlasAccessor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Puts repainted sprites into their atlases from 1.21.11, where a still sprite goes into the atlas texture with its
 * padding and an animated sprite keeps each frame in a texture of its own.
 */
final class DeviceAtlasUpload {
    private DeviceAtlasUpload() {}

    static void upload(List<TextureUploads.Pending> pending) {
        // Still sprites: each mip level with its padding, as it goes into the atlas.
        NativeImage[][] padded = new NativeImage[pending.size()][];
        try {
            IntStream.range(0, pending.size()).parallel().forEach(i -> {
                TextureUploads.Pending p = pending.get(i);
                p.repaint();
                if (!p.sprite.contents().isAnimated()) padded[i] = padded(p.sprite.contents(), SpriteAccess.padding(p.sprite, p.atlas.getTexture().getWidth(0)));
            });
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            Map<TextureAtlas, List<TextureAtlasSprite>> animated = new LinkedHashMap<>();
            for (int i = 0; i < pending.size(); i++) {
                TextureUploads.Pending p = pending.get(i);
                if (padded[i] == null) {
                    animated.computeIfAbsent(p.atlas, a -> new ArrayList<>()).add(p.sprite);
                    continue;
                }
                for (int level = 0; level < padded[i].length; level++) {
                    NativeImage image = padded[i][level];
                    int x = p.sprite.getX() >> level, y = p.sprite.getY() >> level;
                    //? if >=26.2 {
                    encoder.writeToTexture(p.atlas.getTexture(), image, level, 0, x, y);
                    //?} else {
                    /*encoder.writeToTexture(p.atlas.getTexture(), image, level, 0, x, y, image.getWidth(), image.getHeight(), 0, 0);
                    *///?}
                }
            }
            animated.forEach((atlas, sprites) -> replaceAnimations((TextureAtlasAccessor) atlas, sprites));
        } finally {
            for (NativeImage[] images : padded) {
                if (images != null) for (NativeImage image : images) image.close();
            }
        }
    }

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
}
//?}
