package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Every loaded texture with a copy of its original pixels, safe for the worker threads that load textures on modern versions. */
public final class TextureStore {
    /** How an adapter writes new pixels into a loaded texture on the client thread. */
    public interface Upload {
        /**
         * @param pixels the new pixels, the same size as the original
         * @return false when a reload has replaced the texture
         */
        boolean upload(PixelBuffer pixels) throws Exception;
    }

    /** The upload for a texture type from a different mod, which only a resource reload can change. */
    public static final Upload RELOAD_ONLY = pixels -> {
        throw new IllegalStateException("a reload-only texture has no upload");
    };

    public static final class Result {
        public final int repainted;
        /** Textures whose new look needs a resource reload, because their upload failed or is {@link #RELOAD_ONLY}. */
        public final int needReload;

        Result(int repainted, int needReload) {
            this.repainted = repainted;
            this.needReload = needReload;
        }
    }

    private static final class Entry {
        final TextureKey key;
        final PixelBuffer.Heap original;
        final Upload upload;
        final int average;
        /** The look the texture has in the game, or null when it is not known. */
        volatile Appearance applied;

        Entry(TextureKey key, PixelBuffer.Heap original, Upload upload, Appearance applied) {
            this.key = key;
            this.original = original;
            this.upload = upload;
            this.average = PixelTransformer.average(original);
            this.applied = applied;
        }

        long bytes() { return 4L * original.width() * original.height(); }
    }

    /** Above this many bytes of original pixels (very large resource packs), changes fall back to a reload. */
    static final long DEFAULT_BUDGET = 128L << 20;

    private final long budget;
    /** By texture name, because some textures share a key, for example the OptiFine connected textures of one block. */
    private final Map<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    private final AtomicLong bytes = new AtomicLong();
    /** Names of textures that did not fit in the budget until a smaller record of the same name. */
    private final Set<String> overBudget = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    /** Set on the thread that repaints, so that a load hook that the upload runs does not record the texture again. */
    private final ThreadLocal<Boolean> repainting = new ThreadLocal<Boolean>();

    private final AtomicInteger version = new AtomicInteger();
    private final Map<Category, Map<String, Integer>> views = new EnumMap<Category, Map<String, Integer>>(Category.class);
    private int viewsVersion = -1;

    public TextureStore() {
        this(DEFAULT_BUDGET);
    }

    TextureStore(long budget) {
        this.budget = budget;
    }

    /** Whether every changeable texture has its original, so that {@link #repaint} can reach all of them. */
    public boolean complete() {
        return overBudget.isEmpty();
    }

    /** True on the thread of a running {@link #repaint}, where load hooks must leave the image alone. */
    public boolean repainting() {
        return Boolean.TRUE.equals(repainting.get());
    }

    /**
     * Keeps a copy of {@code image} before the caller changes it, unless no setting changes it or {@code upload} is null.
     *
     * @param name the texture name, for example "minecraft:blocks/stone", which replaces a kept texture of that name
     * @param upload null for a copy that must not replace the kept texture, for example a skin placeholder
     * @param applied the look the caller gives the image now
     */
    public void record(String name, TextureKey key, PixelBuffer image, Upload upload, Appearance applied) {
        if (key.category() == Category.OTHER || upload == null) return;
        Entry next = new Entry(key, PixelBuffer.Heap.copyOf(image), upload, applied);
        synchronized (this) { // worker threads record at the same time on modern versions
            Entry before = entries.get(name);
            long delta = next.bytes() - (before == null ? 0 : before.bytes());
            if (bytes.get() + delta > budget) {
                overBudget.add(name);
                return;
            }
            overBudget.remove(name);
            bytes.addAndGet(delta);
            entries.put(name, next);
            if (before == null || before.average != next.average) version.incrementAndGet();
        }
    }

    /** Makes the next {@link #repaint} paint this texture, whose look in the game is not known. */
    public void invalidate(String name) {
        Entry e = entries.get(name);
        if (e != null) e.applied = null;
    }

    /** Gives each kept texture the look {@code policy} wants, and repaints user images again when they changed. */
    public Result repaint(Policy policy, PixelTransformer.TextureSource images, boolean userImagesChanged) {
        int repainted = 0, needReload = 0;
        repainting.set(Boolean.TRUE);
        try {
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                Entry entry = e.getValue();
                Appearance want = policy.appearanceFor(entry.key);
                boolean userImage = userImagesChanged && want.mode() == Appearance.Mode.TEXTURE;
                if (want.equals(entry.applied) && !userImage) continue;
                if (entry.upload == RELOAD_ONLY) {
                    needReload++;
                    continue;
                }
                try {
                    PixelBuffer.Heap pixels = entry.original.copy();
                    PixelTransformer.apply(want, pixels, images);
                    if (entry.upload.upload(pixels)) {
                        entry.applied = want;
                        repainted++;
                    } else {
                        forget(e.getKey(), entry);
                    }
                } catch (Exception ex) {
                    needReload++;
                }
            }
        } finally {
            repainting.remove();
        }
        return new Result(repainted, needReload);
    }

    /** Drops a texture that a reload replaced, unless the reload has recorded it again in the meantime. */
    private synchronized void forget(String name, Entry entry) {
        if (entries.remove(name, entry)) {
            bytes.addAndGet(-entry.bytes());
            version.incrementAndGet();
        }
    }

    /** Ids kept for a category in name order, each with its original average color, for the client thread. */
    public Map<String, Integer> catalog(Category category) {
        int v = version.get();
        if (v != viewsVersion) {
            views.clear();
            viewsVersion = v;
        }
        Map<String, Integer> view = views.get(category);
        if (view == null) {
            Map<String, Integer> out = new TreeMap<String, Integer>();
            // In name order, so that of the textures with one id the first name (the vanilla one) gives the color.
            for (Entry e : new TreeMap<String, Entry>(entries).values()) {
                if (e.key.category() == category && !out.containsKey(e.key.id())) out.put(e.key.id(), e.average);
            }
            view = Collections.unmodifiableMap(out);
            views.put(category, view);
        }
        return view;
    }

    /** An upload into a pixel array that the game keeps by reference, such as a biome colormap. */
    public static Upload intoArray(final int[] target) {
        return pixels -> {
            ((PixelBuffer.Heap) pixels).copyInto(target);
            return true;
        };
    }
}
