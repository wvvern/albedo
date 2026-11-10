package dev.albedo.modern;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.texture.AtlasPaths;
import dev.albedo.core.texture.ChromaTexels;
import dev.albedo.modern.mixin.TextureAtlasAccessor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moves the Chroma colors of the atlas sprites in each frame on the GPU: at the start of a frame the pass draws the hue of
 * each moving texel into the atlas textures, and at the end of the frame the atlases get back the colors that Albedo
 * painted, so that the game and Albedo always write into those colors.
 */
final class ChromaAtlases {
    //? if >=26.3 {
    private static final ChromaBackend BACKEND = new ChromaPass();
    //?} elif >=1.21.11 {
    /*private static final ChromaBackend BACKEND = new PrecompiledChromaPass();
    *///?} else {
    /*private static final ChromaBackend BACKEND = new GlChromaPass();
    *///?}

    /** The atlases whose sprites can have their hue from the pass, by location, which the sprite loaders add on worker threads. */
    private static final Set<Object> ATLASES = ConcurrentHashMap.newKeySet();
    /** The GPU target of each atlas with moving texels, by location. */
    private static final Map<Object, ChromaBackend.Target> TARGETS = new HashMap<>();
    /**
     * The sprite map of each atlas when its texels were made, by location, because 1.21.1 uploads a reloaded atlas of the
     * same size into the same GL texture, and the atlas gets a new map in each upload.
     */
    private static final Map<Object, Object> LAYOUTS = new HashMap<>();
    private static final float[] TURNS = new float[Chroma.TURN_FLOATS];
    /** The texture paints of the core when the texels were made, so that a change makes them again. */
    private static int paints = Integer.MIN_VALUE;
    /** True from the pass at the start of a frame until the atlases get back their painted colors. */
    private static boolean recolored;
    /** True after the pass failed, so that it does not run again in this game. */
    private static boolean broken;
    /** The clock time of the colors of this frame. */
    private static double seconds;

    /** The frames that the harness still measures, and the times of the measured frames. */
    private static int timedLeft;
    private static final List<ChromaBackend.Timer[]> PENDING = new ArrayList<>();
    private static final List<long[]> TIMES = new ArrayList<>();
    private static ChromaBackend.Timer[] frameTimers;
    private static long cpuStart;

    private ChromaAtlases() {}

    /** Whether the pass adds the hue of the moving sprites of an atlas, which also keeps the atlas for the pass. */
    static boolean gpuHue(Object atlas) {
        ATLASES.add(atlas);
        Albedo sc = Albedo.get();
        return !broken && sc != null && !sc.chromaStill();
    }

    /** True while the pass moves the colors of some atlas, so that the screens draw their item icons again in each frame. */
    static boolean moving() {
        return !TARGETS.isEmpty();
    }

    /** Draws the colors of this frame into the atlases, on the render thread before the frame draws anything. */
    static void frameStart() {
        Albedo sc = Albedo.get();
        if (sc == null || broken) return;
        // The end of the last frame did not run when its render stopped early, so its colors must not go into the pristine copy.
        restore();
        if (sc.chromaStill()) {
            close();
            return;
        }
        long start = System.nanoTime();
        try {
            int now = Albedo.texturePaints();
            boolean changed = now != paints;
            paints = now;
            for (Object id : ATLASES) {
                AbstractTexture texture = TextureUploads.registered(id);
                ChromaBackend.Target target = TARGETS.get(id);
                if (!(texture instanceof TextureAtlas atlas)) {
                    if (target != null) TARGETS.remove(id).close();
                    LAYOUTS.remove(id);
                    continue;
                }
                boolean newLayout = LAYOUTS.get(id) != ((TextureAtlasAccessor) atlas).albedo$texturesByName();
                if (changed || newLayout || target != null && !target.sameTexture(atlas)) build(id, atlas);
            }
            if (TARGETS.isEmpty()) return;
            ChromaBackend.Shader shader = BACKEND.compile();
            if (shader == ChromaBackend.Shader.FAILED) throw new IllegalStateException("the Chroma shader does not compile");
            if (shader == ChromaBackend.Shader.COMPILING) return;
            seconds = SkyClock.longSeconds();
            Chroma.turns(seconds, TURNS);
            if (timedLeft > 0) frameTimers = new ChromaBackend.Timer[]{BACKEND.timer(), BACKEND.timer()};
            if (frameTimers != null) frameTimers[0].begin();
            for (ChromaBackend.Target target : TARGETS.values()) target.recolor(TURNS);
            if (frameTimers != null) frameTimers[0].end();
            recolored = true;
        } catch (RuntimeException e) {
            stop(e);
        }
        cpuStart = System.nanoTime() - start;
    }

    /** Gives the atlases back their painted colors at the end of a frame, after the harness saw the frame. */
    static void frameEnd() {
        Albedo sc = Albedo.get();
        if (sc != null) sc.frameDrawn(recolored ? seconds : SkyClock.longSeconds());
        long start = System.nanoTime();
        if (frameTimers != null) frameTimers[1].begin();
        restore();
        if (frameTimers != null) {
            frameTimers[1].end();
            PENDING.add(frameTimers);
            TIMES.add(new long[]{cpuStart + System.nanoTime() - start});
            frameTimers = null;
            timedLeft--;
        }
        if (!PENDING.isEmpty()) collectTimes();
    }

    /** Gives the atlases back their painted colors before Albedo writes new sprites into them in the middle of a frame. */
    static void restore() {
        if (!recolored) return;
        recolored = false;
        try {
            for (ChromaBackend.Target target : TARGETS.values()) target.restore();
        } catch (RuntimeException e) {
            stop(e);
        }
    }

    /** Makes the texels of an atlas from its sprites, and keeps a GPU target only for an atlas with moving texels. */
    private static void build(Object id, TextureAtlas atlas) {
        ChromaBackend.Target old = TARGETS.remove(id);
        try {
            buildTarget(id, atlas, old);
        } finally {
            // The old target closes here unless the atlas keeps it, also when a step throws.
            if (old != null && TARGETS.get(id) != old) old.close();
        }
    }

    /** Makes the texels of an atlas and puts its target, the old one when it fits, in place for an atlas with moving texels. */
    private static void buildTarget(Object id, TextureAtlas atlas, ChromaBackend.Target old) {
        Map<?, TextureAtlasSprite> sprites = ((TextureAtlasAccessor) atlas).albedo$texturesByName();
        LAYOUTS.put(id, sprites);
        int[] size = BACKEND.size(atlas);
        if (size == null) {
            paints = Integer.MIN_VALUE; // the next frame tries again
            return;
        }
        int width = size[0], height = size[1];
        ChromaTexels.Atlas texels = new ChromaTexels.Atlas(width, height);
        String atlasName = String.valueOf(id);
        for (TextureAtlasSprite sprite : sprites.values()) {
            //? if >=1.21.11 {
            // u0 = (x + padding) / atlas width, and the padding repeats the edge pixels of the sprite.
            int padding = Math.round(sprite.getU0() * width) - sprite.getX();
            //?} else {
            /*int padding = 0;
            *///?}
            texels.sprite(AtlasPaths.spriteLocation(atlasName, String.valueOf(sprite.contents().name())), sprite.getX(), sprite.getY(),
                    sprite.contents().width(), sprite.contents().height(), padding);
        }
        if (!texels.moving()) return;
        if (BACKEND.compile() == ChromaBackend.Shader.FAILED) throw new IllegalStateException("the Chroma shader does not compile");
        int[][] levels = texels.levels(size[2]);
        ChromaBackend.Target target = old;
        if (target == null || !target.sameTexture(atlas)) {
            target = BACKEND.create(atlas, size);
            Albedo.get().platform().info(String.format(Locale.ROOT, "Moves the Chroma colors of %s on the GPU: %dx%d with %d mip levels, %.1f MiB",
                    atlasName, width, height, size[2], target.bytes() / 1048576.0));
        }
        TARGETS.put(id, target);
        target.texels(levels);
    }

    /** Stops the pass for this game after an error, and lets the core paint still Chroma colors in place of it. */
    private static void stop(RuntimeException e) {
        broken = true;
        recolored = false;
        Albedo.get().platform().warn("Cannot move the Chroma colors on the GPU. They stay still.", e);
        close();
        Albedo.get().chromaGpu(false);
    }

    private static void close() {
        restore();
        for (ChromaBackend.Target target : TARGETS.values()) target.close();
        TARGETS.clear();
        LAYOUTS.clear();
        paints = Integer.MIN_VALUE;
    }

    /** Measures the next frames for the harness command chromatime, and returns false when the pass does not run. */
    static boolean time(int frames) {
        if (TARGETS.isEmpty() || broken) return false;
        // A measurement that did not end yet gives way to the new one, and its timers close.
        for (ChromaBackend.Timer[] t : PENDING) closeTimers(t);
        if (frameTimers != null) closeTimers(frameTimers);
        frameTimers = null;
        TIMES.clear();
        PENDING.clear();
        timedLeft = frames;
        return true;
    }

    private static void closeTimers(ChromaBackend.Timer[] timers) {
        for (ChromaBackend.Timer t : timers) t.close();
    }

    /** Takes the GPU times that the GPU gave, and logs all times when the last measured frame has its times. */
    private static void collectTimes() {
        int done = TIMES.size() - PENDING.size();
        for (Iterator<ChromaBackend.Timer[]> it = PENDING.iterator(); it.hasNext(); done++) {
            ChromaBackend.Timer[] t = it.next();
            long start = t[0].nanos(), end = t[1].nanos();
            if (start < 0 || end < 0) return;
            long[] row = TIMES.get(done);
            TIMES.set(done, new long[]{row[0], start, end});
            closeTimers(t);
            it.remove();
        }
        if (timedLeft > 0) return;
        long bytes = 0;
        StringBuilder atlases = new StringBuilder();
        for (Map.Entry<Object, ChromaBackend.Target> e : TARGETS.entrySet()) {
            bytes += e.getValue().bytes();
            atlases.append(atlases.length() == 0 ? "" : ", ").append(e.getKey());
        }
        Harness.logChromaTimes(TIMES, bytes, atlases.toString());
        TIMES.clear();
    }
}
