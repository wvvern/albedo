package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.texture.ChromaTexels;
import dev.albedo.core.texture.ChromaTiming;
import dev.albedo.legacy.mixin.TextureMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;

import java.util.Arrays;
import java.util.Locale;

/**
 * Moves the Chroma colors of the block atlas in each frame on 1.8.9 and 1.12.2 with an OpenGL 2.0 program and a framebuffer: at the
 * start of a frame the pass draws the hue of each moving texel into each mip level of the atlas, and at the end of the
 * frame the atlas gets back the colors that Albedo painted, so that the game and Albedo always write into those colors.
 */
public final class LegacyChroma {
    private static final double BYTES_PER_MIB = 1048576.0;

    private static LegacyChromaTarget.Program program;
    private static final float[] TURN_VALUES = new float[Chroma.TURN_FLOATS];
    /** The OptiFine Multitexture setting of the last frame, with which the blocks draw from a texture of each sprite. */
    private static boolean lastMultiTexture;

    /** The GPU textures of the atlas, or null without moving texels. */
    private static LegacyChromaTarget target;
    /** The texture paints of the core when the texels were made, so that a change makes them again. */
    private static int paints = Integer.MIN_VALUE;
    /** True from the pass at the start of a frame until the atlas gets back its painted colors. */
    private static boolean recolored;
    /** True after the pass failed, so that it does not run again in this game. */
    private static boolean broken;
    /** The clock time of the colors of this frame. */
    private static double seconds;

    private static final ChromaTiming TIMING = new ChromaTiming(LegacyGpuTimer::new, Harness::logChromaTimes);

    private LegacyChroma() {}

    /** Whether the pass adds the hue of the moving sprites of the block atlas. */
    static boolean gpuHue() {
        Albedo albedo = Albedo.get();
        return !broken && albedo != null && albedo.chromaGpuRunning();
    }

    /** Draws the colors of this frame into the atlas, before the frame draws anything. */
    public static void frameStart() {
        Albedo albedo = Albedo.get();
        if (albedo == null || broken) return;
        // The end of the last frame did not run when its render stopped early, so its colors must not go into the pristine copy.
        restore();
        // The pass cannot reach the texture of each sprite that OptiFine Multitexture draws, so Chroma is then off.
        boolean multiTexture = OptiFine.multiTexture();
        if (multiTexture != lastMultiTexture) {
            lastMultiTexture = multiTexture;
            albedo.chromaGpu(!multiTexture);
        }
        if (!albedo.chromaGpuRunning()) {
            close();
            return;
        }
        long start = System.nanoTime();
        try {
            TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
            if (atlas == null) return;
            int now = Albedo.texturePaints();
            if (now != paints || target != null && !Arrays.equals(target.size, sizeOf(atlas))) build(atlas);
            paints = now;
            if (target == null) return;
            seconds = SkyClock.longSeconds();
            Chroma.turns(seconds, TURN_VALUES);
            TIMING.frameBegin();
            TIMING.begin(ChromaTiming.Part.RECOLOR);
            target.recolor(TURN_VALUES);
            TIMING.end(ChromaTiming.Part.RECOLOR);
            recolored = true;
        } catch (RuntimeException e) {
            stop(e);
        }
        TIMING.recolorCpu(System.nanoTime() - start);
    }

    /** Gives the atlas back its painted colors at the end of a frame, after the harness saw the frame. */
    public static void frameEnd() {
        Albedo albedo = Albedo.get();
        if (albedo != null) albedo.frameDrawn(recolored ? seconds : SkyClock.longSeconds());
        long start = System.nanoTime();
        TIMING.begin(ChromaTiming.Part.RESTORE);
        restore();
        TIMING.end(ChromaTiming.Part.RESTORE);
        TIMING.frameEnd(System.nanoTime() - start);
        if (TIMING.collecting()) TIMING.collect(target == null ? 0 : target.bytes(), "the block atlas");
    }

    /** Gives the atlas back its painted colors before Albedo or the game writes new sprites into it in the middle of a frame. */
    public static void restore() {
        if (!recolored) return;
        recolored = false;
        try {
            target.restore();
        } catch (RuntimeException e) {
            stop(e);
        }
    }

    /** Makes the texels of the atlas from its sprites, and keeps the GPU textures only while some texels move. */
    private static void build(TextureMap atlas) {
        int[] now = sizeOf(atlas);
        ChromaTexels.Atlas built = new ChromaTexels.Atlas(now[0], now[1], Albedo::chromaTexels);
        for (TextureAtlasSprite sprite : ((TextureMapAccessor) atlas).albedo$sprites().values()) {
            built.sprite(sprite.getIconName(), sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight(), 0);
        }
        if (!built.moving()) {
            close();
            return;
        }
        if (!compile()) throw new IllegalStateException("the Chroma shader does not compile");
        if (target == null || target.live != atlas.getGlTextureId() || !Arrays.equals(target.size, now)) {
            close();
            target = new LegacyChromaTarget(program, atlas.getGlTextureId(), now);
            Albedo.get().platform().info(String.format(Locale.ROOT, "Moves the Chroma colors of the block atlas on the GPU: %dx%d with %d mip levels,"
                    + " %.1f MiB", now[0], now[1], now[2], target.bytes() / BYTES_PER_MIB));
        }
        target.texels(built.levels(target.size[2]));
    }

    /** The width, height and number of mip levels of the GL texture of the atlas. */
    private static int[] sizeOf(TextureMap atlas) {
        return LegacyChromaTarget.sizeOf(program == null ? 0 : program.framebuffer(), atlas.getGlTextureId());
    }

    /** Compiles the program on its first use, and returns true when it compiled. */
    private static boolean compile() {
        if (program == null) program = new LegacyChromaTarget.Program();
        return program.ready();
    }

    /** Stops the pass for this game after an error, and lets the core paint the sprites again without Chroma. */
    private static void stop(RuntimeException e) {
        broken = true;
        recolored = false;
        Albedo.get().platform().warn("Cannot move the Chroma colors on the GPU. Chroma is off.", e);
        close();
        Albedo.get().chromaGpu(false);
    }

    private static void close() {
        restore();
        if (target != null) target.close();
        target = null;
        paints = Integer.MIN_VALUE;
    }

    /** Measures the next frames for the harness command chromatime, and returns false when the pass does not run. */
    static boolean time(int frames) {
        if (target == null || broken) return false;
        TIMING.start(frames);
        return true;
    }
}
