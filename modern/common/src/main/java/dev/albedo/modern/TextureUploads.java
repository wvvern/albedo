package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.texture.TextureStore;
import dev.albedo.modern.mixin.TextureManagerAccessor;
import dev.albedo.modern.mixin.TextureUploadInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts repainted pixels back into a loaded texture and its GPU copy on the render thread, so that a settings change
 * needs no resource reload; the upload class of the version ({@code DeviceAtlasUpload} or {@code GlAtlasUpload}) sends the
 * atlas sprites.
 */
final class TextureUploads {
    private TextureUploads() {}

    /** A sprite with new pixels, which {@link #flush} puts into the sprite and its atlas. */
    static final class Pending {
        final TextureAtlas atlas;
        final TextureAtlasSprite sprite;
        private final SpriteAccess access;
        private final PixelBuffer pixels;

        Pending(TextureAtlas atlas, TextureAtlasSprite sprite, SpriteAccess access, PixelBuffer pixels) {
            this.atlas = atlas;
            this.sprite = sprite;
            this.access = access;
            this.pixels = pixels;
        }

        /** Writes the full image (every animation frame), then makes the mip levels from it. */
        void repaint() {
            write(pixels, access.image());
            access.remip(sprite);
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Uploads into an atlas sprite only while the game still uses it, because a reload (a resource pack change or F3+T)
     * replaces sprites and textures and the load hooks then record new uploads.
     *
     * @param atlasId the location of the atlas texture
     */
    static TextureStore.Upload sprite(Object atlasId, SpriteAccess access) {
        return new TextureStore.Upload() {
            @Override
            public boolean upload(PixelBuffer pixels) {
                if (!(registered(atlasId) instanceof TextureAtlas atlas)) return false;
                TextureAtlasSprite sprite = access.in(atlas);
                if (sprite == null) return false;
                NativeImage original = access.image();
                if (original.getWidth() != pixels.width() || original.getHeight() != pixels.height()) return false;
                PENDING.add(new Pending(atlas, sprite, access, pixels));
                return true;
            }

            @Override
            public boolean gpuHue() {
                return ChromaAtlases.gpuHue(atlasId);
            }
        };
    }

    /** Puts the repainted sprites into their atlases, and must run after each repaint, also when an upload failed. */
    static void flush() {
        // The new sprites go into the colors that Albedo painted, which the Chroma pass copies at the start of the next frame.
        if (!PENDING.isEmpty()) ChromaAtlases.restore();
        try {
            //? if >=1.21.11 {
            DeviceAtlasUpload.upload(PENDING);
            //?} else {
            /*GlAtlasUpload.upload(PENDING);
            *///?}
        } finally {
            PENDING.clear();
        }
    }

    //? if >=1.21.11 {
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
    //?} elif >=1.21.4 {
    /*// A stand-alone texture, loaded again by the class of the versions up to 1.21.10.
    static TextureStore.Upload texture(Object location) {
        return ReloadableTextureUpload.texture(location);
    }
    *///?} else {
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

    //? if >=1.21.4 {
    /** A downloaded skin, cape or elytra, which keeps its image in memory until the game releases it. */
    static TextureStore.Upload downloaded(Object id, net.minecraft.client.renderer.texture.DynamicTexture texture) {
        return pixels -> {
            NativeImage image = texture.getPixels();
            if (registered(id) != texture || image == null) return false; // released, which closed the image
            write(pixels, image);
            texture.upload();
            return true;
        };
    }
    //?} else {
    /*// A downloaded skin, cape or elytra, written with the upload step of HttpTexture.
    static TextureStore.Upload downloaded(Object id, DownloadedSkin texture) {
        return pixels -> {
            if (registered(id) != texture) return false;
            NativeImage image = new NativeImage(pixels.width(), pixels.height(), false);
            write(pixels, image);
            texture.albedo$upload(image);
            return true;
        };
    }
    *///?}

    /** The texture registered at {@code location} now, or null, without making one as TextureManager.getTexture does. */
    static AbstractTexture registered(Object location) {
        return ((TextureManagerAccessor) Minecraft.getInstance().getTextureManager()).albedo$byPath().get(location);
    }

    static void write(PixelBuffer pixels, NativeImage image) {
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
