package dev.albedo.modern;

import dev.albedo.core.ChromaHost;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.texture.ChromaFrames;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The Chroma pass of the atlas sprites on the GPU of each modern version, with the schedule of {@link ChromaFrames}. */
final class ChromaAtlases {
    private static final ChromaBackend BACKEND = backend();

    private static ChromaBackend backend() {
        //? if gl_fixed {
        /*return new FixedChromaPass();
        *///?}
        //? if renderpearl {
        return new ChromaPass();
        //?} elif device_chroma {
        /*return new PrecompiledChromaPass();
        *///?} elif gl_chroma {
        /*return new GlChromaPass();
        *///?}
    }

    /** The atlases whose sprites can have their hue from the pass, by location, which the sprite loaders add on worker threads. */
    private static final Set<Object> ATLASES = ConcurrentHashMap.newKeySet();
    private static final ChromaFrames<TextureAtlas> FRAMES = new ChromaFrames<>(BACKEND, ChromaHost.INSTANCE, Harness::logChromaTimes);

    private ChromaAtlases() {}

    /** Whether the pass adds the hue of the moving sprites of an atlas, which also keeps the atlas for the pass. */
    static boolean gpuHue(Object atlas) {
        ATLASES.add(atlas);
        return FRAMES.gpuHue();
    }

    /** True while the pass moves the colors of some atlas, so that the screens draw their item icons again in each frame. */
    static boolean moving() {
        return FRAMES.moving();
    }

    /** Draws the colors of this frame into the atlases, on the render thread before the frame draws anything. */
    static void frameStart() {
        FRAMES.frameStart(ChromaAtlases::atlases);
    }

    /** Gives the atlases back their painted colors at the end of a frame, after the harness saw the frame. */
    static void frameEnd() {
        FRAMES.frameEnd();
    }

    /** Gives the atlases back their painted colors before Albedo writes new sprites into them in the middle of a frame. */
    static void restore() {
        FRAMES.restore();
    }

    /** Measures the next frames for the harness command chromatime, and returns false when the pass does not run. */
    static boolean time(int frames) {
        return FRAMES.time(frames);
    }

    /** The kept atlases that the texture manager has as atlases now, by location. */
    private static Map<Object, TextureAtlas> atlases() {
        Map<Object, TextureAtlas> out = new LinkedHashMap<>();
        for (Object id : ATLASES) {
            AbstractTexture texture = TextureUploads.registered(id);
            if (texture instanceof TextureAtlas) out.put(id, (TextureAtlas) texture);
        }
        return out;
    }
}
