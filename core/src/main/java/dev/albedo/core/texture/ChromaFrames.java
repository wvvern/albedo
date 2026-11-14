package dev.albedo.core.texture;

import dev.albedo.core.config.Chroma;
import dev.albedo.core.shader.ShaderState;
import dev.albedo.core.sky.SkyClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The frame schedule of the GPU pass of Chroma on the render thread, with the GPU work of the adapter in a {@link Backend}:
 * at the start of a frame the pass draws the hue of each moving texel into the atlas textures, and at the end of the frame
 * the atlases get back the colors that Albedo painted, so that the game and Albedo always write into those colors.
 *
 * @param <A> the atlas type of the game
 */
public final class ChromaFrames<A> {
    private static final double BYTES_PER_MIB = 1048576.0;

    /** The port to the core, where the adapters pass {@code ChromaHost.INSTANCE}, so that {@code texture} does not import {@code Albedo}. */
    public interface Host extends ChromaTexels.Source {
        /** True when the core started and Chroma runs on the GPU. */
        boolean running();

        /** Stops the GPU pass in the core, which then paints the sprites again without Chroma. */
        void stopGpu();

        /** Tells the core that a frame is drawn with the Chroma colors of this clock time. */
        void frameDrawn(double seconds);

        /** {@code Albedo.texturePaints}. */
        int texturePaints();

        void info(String message);

        void warn(String message, Throwable error);
    }

    /** One atlas texture with its pristine copy and its Chroma texels on the GPU. */
    public interface Target<T> extends AutoCloseable {
        /** Whether the atlas still uses the GPU texture of this target, which a resource reload replaces. */
        boolean sameTexture(T atlas);

        /** Sends the Chroma texels of each mip level, as {@link ChromaTexels.Atlas#levels} gives them. */
        void texels(int[][] levels);

        /**
         * Copies each mip level of the atlas into the pristine copy, then draws the pristine copy with the hue of each
         * moving texel turned into the atlas.
         *
         * @param turns the part of one turn of each speed, from {@code Chroma.turns}
         */
        void recolor(float[] turns);

        /** Copies the pristine copy back into each mip level of the atlas. */
        void restore();

        /** The bytes of GPU memory of the pristine copy and the texels. */
        long bytes();

        @Override
        void close();
    }

    /** The GPU work of the pass on one version. */
    public interface Backend<T> {
        /**
         * Compiles the shader of the pass on the render thread when it is not compiled, or gives it to the game again when a
         * version needs that after a resource reload, which the pass calls in each frame before it draws.
         *
         * @return READY, RUNNING while the compile runs, or FAILED
         */
        ShaderState compile();

        /** The size of the GPU texture of an atlas, or null before the atlas has its texture. */
        Size size(T atlas);

        Target<T> create(T atlas, Size size);

        ChromaTiming.GpuTimer timer();

        /** An object that changes when the sprites of the atlas change, which makes the texels again. */
        Object layout(T atlas);

        /** Adds each sprite of an atlas to its texels. */
        void sprites(String atlasName, T atlas, ChromaTexels.Atlas texels);
    }

    /** The width, height and number of mip levels of an atlas texture. */
    public static final class Size {
        public final int width;
        public final int height;
        public final int levels;

        public Size(int width, int height, int levels) {
            this.width = width;
            this.height = height;
            this.levels = levels;
        }

        /**
         * The size of a GL texture with its levels clamped by {@link ChromaTexels#mipLevels}.
         *
         * @param maxLevelPlusOne {@code GL_TEXTURE_MAX_LEVEL} of the texture plus 1
         */
        public static Size clamped(int width, int height, int maxLevelPlusOne) {
            return new Size(width, height, ChromaTexels.mipLevels(width, height, maxLevelPlusOne));
        }

        /** True when the texture has texels. */
        public boolean present() {
            return width > 0 && height > 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Size)) return false;
            Size s = (Size) o;
            return width == s.width && height == s.height && levels == s.levels;
        }

        @Override
        public int hashCode() {
            return (width * 31 + height) * 31 + levels;
        }
    }

    /** The state of the frame: the atlases hold the painted colors, some atlases hold the hue, or the pass failed and stays off. */
    private enum Pass { IDLE, RECOLORED, BROKEN }

    private final Backend<A> backend;
    private final Host host;
    private final ChromaTiming timing;
    /** The GPU target of each atlas with moving texels, by atlas key. */
    private final Map<Object, Target<A>> targets = new HashMap<Object, Target<A>>();
    /** The {@link Backend#layout} of each atlas when its texels were made, by atlas key. */
    private final Map<Object, Object> layouts = new HashMap<Object, Object>();
    /** The targets that the pass recolored at the start of a frame, until their atlases get back their painted colors. */
    private final List<Target<A>> recolored = new ArrayList<Target<A>>();
    private final float[] turns = new float[Chroma.TURN_FLOATS];
    private Pass pass = Pass.IDLE;
    /** The texture paints of the core when the texels were made, so that a change makes them again. */
    private int paints;
    /** True when the next frame makes the texels of each atlas again, whatever the paints. */
    private boolean texelsStale = true;
    /** The clock time of the colors of this frame. */
    private double seconds;

    public ChromaFrames(Backend<A> backend, Host host, ChromaTiming.TimesSink times) {
        this.backend = backend;
        this.host = host;
        this.timing = new ChromaTiming(backend::timer, times);
    }

    /** Whether the pass adds the hue of the moving sprites, so that the core paints them without the hue. */
    public boolean gpuHue() {
        return pass != Pass.BROKEN && host.running();
    }

    /** True after the pass failed, so that it does not run again in this game. */
    public boolean broken() {
        return pass == Pass.BROKEN;
    }

    /** True while the pass moves the colors of some atlas. */
    public boolean moving() {
        return !targets.isEmpty();
    }

    /**
     * Draws the colors of this frame into the atlases, before the frame draws anything.
     *
     * @param atlases the atlases of the game by key, which the pass reads only while Chroma runs; a target whose key is
     *     missing closes
     */
    public void frameStart(Supplier<? extends Map<Object, ? extends A>> atlases) {
        if (pass == Pass.BROKEN) return;
        // The end of the last frame did not run when its render stopped early, so its colors must not go into the pristine copy.
        restore();
        if (!host.running()) {
            close();
            return;
        }
        long start = System.nanoTime();
        try {
            int now = host.texturePaints();
            boolean changed = texelsStale || now != paints;
            paints = now;
            texelsStale = false;
            Map<Object, ? extends A> live = atlases.get();
            drop(live);
            for (Map.Entry<Object, ? extends A> e : live.entrySet()) {
                Object id = e.getKey();
                A atlas = e.getValue();
                Target<A> target = targets.get(id);
                boolean newLayout = layouts.get(id) != backend.layout(atlas);
                if (changed || newLayout || target != null && !target.sameTexture(atlas)) build(id, atlas);
            }
            if (targets.isEmpty()) return;
            ShaderState shader = backend.compile();
            if (shader.dead()) throw new IllegalStateException("the Chroma shader does not compile");
            if (!shader.usable()) return;
            seconds = SkyClock.longSeconds();
            Chroma.turns(seconds, turns);
            timing.frameBegin();
            timing.begin(ChromaTiming.Part.RECOLOR);
            to(Pass.RECOLORED);
            for (Target<A> target : targets.values()) {
                target.recolor(turns);
                recolored.add(target);
            }
            timing.end(ChromaTiming.Part.RECOLOR);
        } catch (RuntimeException e) {
            stop(e);
        }
        timing.recolorCpu(System.nanoTime() - start);
    }

    /** Gives the atlases back their painted colors at the end of a frame, after the harness saw the frame. */
    public void frameEnd() {
        host.frameDrawn(pass == Pass.RECOLORED ? seconds : SkyClock.longSeconds());
        long start = System.nanoTime();
        timing.begin(ChromaTiming.Part.RESTORE);
        restore();
        timing.end(ChromaTiming.Part.RESTORE);
        timing.frameEnd(System.nanoTime() - start);
        if (!timing.collecting()) return;
        long bytes = 0;
        StringBuilder names = new StringBuilder();
        for (Map.Entry<Object, Target<A>> e : targets.entrySet()) {
            bytes += e.getValue().bytes();
            names.append(names.length() == 0 ? "" : ", ").append(e.getKey());
        }
        timing.collect(bytes, names.toString());
    }

    /** Gives the atlases back their painted colors before Albedo or the game writes new sprites into them in the middle of a frame. */
    public void restore() {
        if (recolored.isEmpty()) return;
        to(Pass.IDLE);
        List<Target<A>> done = new ArrayList<Target<A>>(recolored);
        recolored.clear();
        try {
            for (Target<A> target : done) target.restore();
        } catch (RuntimeException e) {
            stop(e);
        }
    }

    /** Measures the next frames for the harness command chromatime, and returns false when the pass does not run. */
    public boolean time(int frames) {
        if (targets.isEmpty() || pass == Pass.BROKEN) return false;
        timing.start(frames);
        return true;
    }

    /** A move to the next pass, where BROKEN stays. */
    private void to(Pass next) {
        if (pass != Pass.BROKEN) pass = next;
    }

    /** Closes the target and forgets the layout of each atlas that is not in {@code live}. */
    private void drop(Map<Object, ? extends A> live) {
        for (Iterator<Map.Entry<Object, Target<A>>> it = targets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, Target<A>> e = it.next();
            if (live.containsKey(e.getKey())) continue;
            it.remove();
            e.getValue().close();
        }
        layouts.keySet().retainAll(live.keySet());
    }

    /** Makes the texels of an atlas from its sprites, and keeps a GPU target only for an atlas with moving texels. */
    private void build(Object id, A atlas) {
        Target<A> old = targets.remove(id);
        try {
            buildTarget(id, atlas, old);
        } finally {
            // The old target closes here unless the atlas keeps it, also when a step throws.
            if (old != null && targets.get(id) != old) old.close();
        }
    }

    /** Makes the texels of an atlas and puts its target, the old one when it fits, in place for an atlas with moving texels. */
    private void buildTarget(Object id, A atlas, Target<A> old) {
        layouts.put(id, backend.layout(atlas));
        Size size = backend.size(atlas);
        if (size == null) {
            texelsStale = true;
            return;
        }
        ChromaTexels.Atlas texels = new ChromaTexels.Atlas(size.width, size.height, host);
        String name = String.valueOf(id);
        backend.sprites(name, atlas, texels);
        if (!texels.moving()) return;
        if (backend.compile().dead()) throw new IllegalStateException("the Chroma shader does not compile");
        int[][] levels = texels.levels(size.levels);
        Target<A> target = old;
        if (target == null || !target.sameTexture(atlas)) {
            target = backend.create(atlas, size);
            host.info(String.format(Locale.ROOT, "Moves the Chroma colors of %s on the GPU: %dx%d with %d mip levels, %.1f MiB",
                    name, size.width, size.height, size.levels, target.bytes() / BYTES_PER_MIB));
        }
        targets.put(id, target);
        target.texels(levels);
    }

    /** Stops the pass for this game after an error, and lets the core paint the sprites again without Chroma. */
    private void stop(RuntimeException e) {
        to(Pass.BROKEN);
        host.warn("Cannot move the Chroma colors on the GPU. Chroma is off.", e);
        close();
        host.stopGpu();
    }

    private void close() {
        restore();
        for (Target<A> target : targets.values()) target.close();
        targets.clear();
        layouts.clear();
        texelsStale = true;
    }
}
