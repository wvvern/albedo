package dev.albedo.modern;

import dev.albedo.core.texture.AtlasPaths;
import dev.albedo.core.texture.ChromaFrames;
import dev.albedo.core.texture.ChromaTexels;
import dev.albedo.core.texture.ChromaTiming;
import dev.albedo.modern.mixin.TextureAtlasAccessor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** The GPU work of the Chroma pass on one version: the atlas textures that it turns, and a GPU timer for the harness. */
interface ChromaBackend extends ChromaFrames.Backend<TextureAtlas> {
    interface Target extends ChromaFrames.Target<TextureAtlas> {}

    /** A GPU timer of this version for the harness timing. */
    interface Timer extends ChromaTiming.GpuTimer {
        /** The {@link #nanos} of a timer that the GPU cannot run. */
        long UNAVAILABLE = 0;
    }

    @Override
    Target create(TextureAtlas atlas, ChromaFrames.Size size);

    @Override
    Timer timer();

    /**
     * The sprite map of the atlas, because 1.21.1 uploads a reloaded atlas of the same size into the same GL texture, and
     * the atlas gets a new map in each upload.
     */
    @Override
    default Object layout(TextureAtlas atlas) {
        return ((TextureAtlasAccessor) atlas).albedo$texturesByName();
    }

    @Override
    default void sprites(String atlasName, TextureAtlas atlas, ChromaTexels.Atlas texels) {
        for (TextureAtlasSprite sprite : ((TextureAtlasAccessor) atlas).albedo$texturesByName().values()) {
            SpriteAccess access = SpriteAccess.of(sprite);
            texels.sprite(AtlasPaths.spriteLocation(atlasName, access.name()), SpriteAccess.x(sprite), SpriteAccess.y(sprite), access.width(), access.height(),
                    SpriteAccess.padding(sprite, texels.width()));
        }
    }
}
