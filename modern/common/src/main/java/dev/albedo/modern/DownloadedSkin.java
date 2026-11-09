package dev.albedo.modern;

import com.mojang.blaze3d.platform.NativeImage;

/** A downloaded skin texture before 1.21.4 (HttpTexture), which SkinTextureMixin implements. */
public interface DownloadedSkin {
    /** Keeps the name that TextureManager.register gives the texture, which the texture itself does not know. */
    void albedo$setId(Object id);

    /** Puts an image into the GPU texture as the first upload does, and closes the image. */
    void albedo$upload(NativeImage image);
}
