package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.ChromaHost;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.texture.ChromaFrames;
import dev.albedo.core.texture.ChromaTexels;
import dev.albedo.core.texture.ChromaTiming;
import dev.albedo.legacy.mixin.TextureMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;

import java.util.Collections;
import java.util.Map;

/**
 * The Chroma pass of the block atlas on 1.8.9 and 1.12.2 with an OpenGL 2.0 program and a framebuffer, with the schedule of
 * {@link ChromaFrames}.
 */
public final class LegacyChroma {
    /** The key of the block atlas, which the log lines show. */
    private static final String BLOCK_ATLAS = "the block atlas";

    private static final ChromaFrames<TextureMap> FRAMES = new ChromaFrames<TextureMap>(new Backend(), ChromaHost.INSTANCE,
            Harness::logChromaTimes);
    /** The OptiFine Multitexture setting of the last frame, with which the blocks draw from a texture of each sprite. */
    private static boolean lastMultiTexture;

    private LegacyChroma() {}

    /** Whether the pass adds the hue of the moving sprites of the block atlas. */
    static boolean gpuHue() {
        return FRAMES.gpuHue();
    }

    /** Draws the colors of this frame into the atlas, before the frame draws anything. */
    public static void frameStart() {
        Albedo albedo = Albedo.get();
        if (albedo == null || FRAMES.broken()) return;
        // The pass cannot reach the texture of each sprite that OptiFine Multitexture draws, so Chroma is then off.
        boolean multiTexture = OptiFine.multiTexture();
        if (multiTexture != lastMultiTexture) {
            lastMultiTexture = multiTexture;
            albedo.chromaGpu(!multiTexture);
        }
        FRAMES.frameStart(LegacyChroma::atlases);
    }

    /** Gives the atlas back its painted colors at the end of a frame, after the harness saw the frame. */
    public static void frameEnd() {
        FRAMES.frameEnd();
    }

    /** Gives the atlas back its painted colors before Albedo or the game writes new sprites into it in the middle of a frame. */
    public static void restore() {
        FRAMES.restore();
    }

    /** Measures the next frames for the harness command chromatime, and returns false when the pass does not run. */
    static boolean time(int frames) {
        return FRAMES.time(frames);
    }

    private static Map<Object, TextureMap> atlases() {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        return atlas == null ? Collections.<Object, TextureMap>emptyMap() : Collections.<Object, TextureMap>singletonMap(BLOCK_ATLAS, atlas);
    }

    /** The GL work of the pass on the block atlas. */
    private static final class Backend implements ChromaFrames.Backend<TextureMap> {
        private LegacyChromaTarget.Program program;

        /** Compiles the program on its first use. */
        @Override
        public ShaderState compile() {
            if (program == null) program = new LegacyChromaTarget.Program();
            return program.ready() ? ShaderState.READY : ShaderState.FAILED;
        }

        @Override
        public ChromaFrames.Size size(TextureMap atlas) {
            return LegacyChromaTarget.sizeOf(program == null ? 0 : program.framebuffer(), atlas.getGlTextureId());
        }

        @Override
        public ChromaFrames.Target<TextureMap> create(TextureMap atlas, ChromaFrames.Size size) {
            return new LegacyChromaTarget(program, atlas.getGlTextureId(), size);
        }

        @Override
        public ChromaTiming.GpuTimer timer() {
            return new LegacyGpuTimer();
        }

        /** The atlas itself, so that only a paint change or a new texture makes the texels again. */
        @Override
        public Object layout(TextureMap atlas) {
            return atlas;
        }

        @Override
        public void sprites(String atlasName, TextureMap atlas, ChromaTexels.Atlas texels) {
            for (TextureAtlasSprite sprite : ((TextureMapAccessor) atlas).albedo$sprites().values()) {
                texels.sprite(sprite.getIconName(), sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight(), 0);
            }
        }
    }
}
