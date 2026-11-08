package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every loaded texture with a copy of its original pixels and a way to put new pixels back. A settings change then
 * repaints only the textures whose look changed, without a resource reload.
 *
 * <p>Thread-safe: modern versions load textures on worker threads. {@link #repaint} runs on the client thread.
 */
public final class TextureStore {
    /** How an adapter writes new pixels into a loaded texture (and the GPU). Called on the client thread. */
    public interface Upload {
        /**
         * @param pixels the new pixels, the same size as the original
         * @return false when the texture is no longer in use (a reload replaced it)
         */
        boolean upload(PixelBuffer pixels) throws Exception;
    }

    /** What a repaint did. */
    public static final class Result {
        public final int repainted;
        /** Textures that could not be repainted: the caller must reload resources. */
        public final int failed;

        Result(int repainted, int failed) {
            this.repainted = repainted;
            this.failed = failed;
        }
    }

    private static final class Entry {
        final TextureKey key;
        final PixelBuffer.Heap original;
        final Upload upload;
        final int average;
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
    /** By texture name: some textures share a key, for example the OptiFine connected textures of one block. */
    private final Map<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    private final AtomicLong bytes = new AtomicLong();
    private volatile boolean complete = true;
    /** Set on the thread that repaints, so an upload that runs a load hook is not recorded again. */
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
        return complete;
    }

    /** True on the thread of a running {@link #repaint}. Load hooks then leave the image alone. */
    public boolean repainting() {
        return Boolean.TRUE.equals(repainting.get());
    }

    /**
     * Keeps a copy of {@code image} (call it before the image is changed). {@code applied} is the look the caller
     * gives it now. Textures of {@link Category#OTHER} are not kept: no setting changes them. A null {@code upload}
     * means that the texture cannot be repainted: it is not kept, and a kept texture with the same name stays.
     *
     * @param name the texture name, for example "minecraft:blocks/stone". A texture with the same name replaces it.
     */
    public void record(String name, TextureKey key, PixelBuffer image, Upload upload, Appearance applied) {
        if (key.category() == Category.OTHER || upload == null) return;
        Entry next = new Entry(key, PixelBuffer.Heap.copyOf(image), upload, applied);
        Entry before = entries.get(name);
        long delta = next.bytes() - (before == null ? 0 : before.bytes());
        if (bytes.get() + delta > budget) {
            complete = false;
            return;
        }
        bytes.addAndGet(delta);
        entries.put(name, next);
        if (before == null || before.average != next.average) version.incrementAndGet();
    }

    /**
     * Gives each kept texture the look {@code policy} wants, where it differs from the look it has. With
     * {@code userImagesChanged}, textures in the user image style are painted again too.
     */
    public Result repaint(Policy policy, PixelTransformer.TextureSource images, boolean userImagesChanged) {
        int repainted = 0, failed = 0;
        repainting.set(Boolean.TRUE);
        try {
            for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
                Entry entry = it.next();
                Appearance want = policy.appearanceFor(entry.key);
                boolean userImage = userImagesChanged && want.mode() == Appearance.Mode.TEXTURE;
                if (want.equals(entry.applied) && !userImage) continue;
                PixelBuffer.Heap pixels = entry.original.copy();
                PixelTransformer.apply(want, pixels, images);
                try {
                    if (entry.upload.upload(pixels)) {
                        entry.applied = want;
                        repainted++;
                    } else {
                        it.remove();
                        bytes.addAndGet(-entry.bytes());
                    }
                } catch (Exception ex) {
                    failed++;
                }
            }
        } finally {
            repainting.remove();
        }
        return new Result(repainted, failed);
    }

    /** Ids kept for a category, sorted, each with its original average color. Read-only; client thread. */
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

    /** An upload into a pixel array that the game keeps by reference (the biome colormaps). */
    public static Upload intoArray(final int[] target) {
        return new Upload() {
            @Override
            public boolean upload(PixelBuffer pixels) {
                ((PixelBuffer.Heap) pixels).copyInto(target);
                return true;
            }
        };
    }
}
