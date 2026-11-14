package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every loaded texture with a copy of its original pixels, safe for the worker threads that load textures on modern versions:
 * record and forget lock the store, and repaint does not lock, because record puts a new entry in place of an old one and
 * never changes an entry that a repaint can read.
 */
public final class TextureStore {
    /** How an adapter writes new pixels into a loaded texture on the client thread. */
    public interface Upload {
        /**
         * @param pixels the new pixels, the same size as the original
         * @return false when a reload has replaced the texture
         */
        boolean upload(PixelBuffer pixels) throws Exception;

        /**
         * Whether a GPU pass of the adapter adds the hue of moving Chroma colors to this texture in each frame, so that
         * a paint leaves the hue out, which only an atlas sprite can have.
         */
        default boolean gpuHue() {
            return false;
        }
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
        final Layout layout;
        final PixelBuffer.Heap original;
        final Upload upload;
        /** The original average color of each catalog key of the layout. */
        final int[] averages;
        /** True for a skin, cape or elytra that the game downloads, which a resource reload cannot change. */
        final boolean downloaded;
        /** The looks the texture has in the game, as {@link Layout#looks} gives them, or null when they are not known. */
        volatile List<Appearance> applied;
        /** Whether the texture in the game has its paint for a GPU pass that adds the hue of moving Chroma colors. */
        volatile boolean gpuHue;

        Entry(Layout layout, PixelBuffer.Heap original, Upload upload, List<Appearance> applied, boolean downloaded) {
            this.layout = layout;
            this.original = original;
            this.upload = upload;
            this.averages = layout.averages(original);
            this.applied = applied;
            this.downloaded = downloaded;
            this.gpuHue = upload.gpuHue();
        }

        long bytes() { return 4L * original.width() * original.height(); }
    }

    /** Above this many bytes of original pixels (very large resource packs), changes fall back to a reload. */
    static final long DEFAULT_BUDGET = 128L << 20;

    private final long budget;
    /** By texture name, because some textures share a key, for example the OptiFine connected textures of one block. */
    private final Map<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    private final AtomicLong bytes = new AtomicLong();
    /** Bytes of the downloaded textures, which are not in the budget because only a repaint can change them. */
    private final AtomicLong downloadedBytes = new AtomicLong();
    /** The number of kept textures with each catalog key, so that a downloaded texture changes the catalog only when its key comes or goes. */
    private final Map<TextureKey, Integer> keyCounts = new HashMap<TextureKey, Integer>();
    /** Names of textures that did not fit in the budget until a smaller record of the same name. */
    private final Set<String> overBudget = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    /** Set on the thread that repaints, so that a load hook that the upload runs does not record the texture again. */
    private final ThreadLocal<Boolean> repainting = new ThreadLocal<Boolean>();

    private final AtomicInteger version = new AtomicInteger();
    private final Map<Category, Map<String, Integer>> views = new EnumMap<Category, Map<String, Integer>>(Category.class);
    private int viewsVersion = -1;
    private final AtomicInteger paints = new AtomicInteger();
    /** Counts each record and drop of any texture, for the cache of {@link #movingChroma}. */
    private final AtomicInteger changes = new AtomicInteger();
    /** The categories with a key whose kept textures all have GPU hue, at the {@link #changes} in {@link #movingChromaAt}. */
    private volatile Set<Category> movingChroma = Collections.emptySet();
    private volatile int movingChromaAt = -1;

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
     * @param applied the looks the caller gives the image now, from {@link Layout#looks}, or null when they are not known
     * @return true when the store keeps the texture, which it does not for a texture over the budget
     */
    public boolean record(String name, Layout layout, PixelBuffer image, Upload upload, List<Appearance> applied) {
        if (layout.key().category() == Category.OTHER || upload == null) return false;
        boolean downloaded = TextureClassifier.isDownloaded(name);
        Entry next = new Entry(layout, PixelBuffer.Heap.copyOf(image), upload, applied, downloaded);
        synchronized (this) { // worker threads record at the same time on modern versions
            Entry before = entries.get(name);
            long delta = next.bytes() - (before == null ? 0 : before.bytes());
            if (downloaded) {
                downloadedBytes.addAndGet(delta);
            } else {
                if (bytes.get() + delta > budget) {
                    overBudget.add(name);
                    return false;
                }
                overBudget.remove(name);
                bytes.addAndGet(delta);
            }
            entries.put(name, next);
            changes.incrementAndGet();
            if (movesOnGpu(next, applied) || before != null && movesOnGpu(before, before.applied)) paints.incrementAndGet();
            boolean keysChanged = count(next, 1);
            if (before != null) keysChanged |= count(before, -1);
            // Many downloaded skins share one key, so a new skin changes the catalog only when it adds the key.
            boolean newColors = !downloaded && (before == null || !Arrays.equals(before.averages, next.averages));
            if (keysChanged || newColors) version.incrementAndGet();
        }
        return true;
    }

    /** Whether a GPU pass adds the hue to a texture with these looks, so that its Chroma texels count, where null looks are not known. */
    private static boolean movesOnGpu(Entry e, List<Appearance> looks) {
        if (!e.gpuHue) return false;
        if (looks == null) return true;
        for (Appearance a : looks) if (a.chromaMoves()) return true;
        return false;
    }

    /**
     * Adds {@code delta} to the count of each catalog key of an entry, under the lock.
     *
     * @return true when a key comes into the catalog or leaves it
     */
    private boolean count(Entry e, int delta) {
        boolean changed = false;
        for (TextureKey k : e.layout.catalogKeys()) {
            Integer before = keyCounts.get(k);
            int now = (before == null ? 0 : before) + delta;
            if (now <= 0) keyCounts.remove(k);
            else keyCounts.put(k, now);
            changed |= before == null || now <= 0;
        }
        return changed;
    }

    /** Bytes of the kept downloaded skins, capes and elytra. */
    public long downloadedBytes() {
        return downloadedBytes.get();
    }

    /** Makes the next {@link #repaint} paint this texture, whose look in the game is not known. */
    public void invalidate(String name) {
        Entry e = entries.get(name);
        if (e != null) e.applied = null;
    }

    /** Makes the next {@link #repaint} paint each texture whose {@link Upload#gpuHue} changed since its last paint. */
    public void invalidateGpuHue() {
        for (Entry e : entries.values()) {
            if (e.gpuHue != e.upload.gpuHue()) e.applied = null;
        }
    }

    /** Gives each kept texture the look {@code policy} wants. */
    public Result repaint(Policy policy) {
        int repainted = 0, needReload = 0;
        boolean moved = false;
        repainting.set(Boolean.TRUE);
        try {
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                Entry entry = e.getValue();
                List<Appearance> want = entry.layout.looks(policy);
                if (want.equals(entry.applied)) continue;
                if (entry.upload == RELOAD_ONLY) {
                    needReload++;
                    continue;
                }
                boolean gpuHue = entry.upload.gpuHue();
                // With the hue from the GPU, the pixels do not depend on the Chroma speed, so only the texels change.
                if (entry.gpuHue && gpuHue && onlySpeedChanges(entry.applied, want)) {
                    entry.applied = want;
                    moved = true;
                    continue;
                }
                moved |= movesOnGpu(entry, entry.applied);
                try {
                    PixelBuffer.Heap pixels = entry.original.copy();
                    entry.layout.paint(want, pixels, gpuHue);
                    if (entry.upload.upload(pixels)) {
                        entry.applied = want;
                        entry.gpuHue = gpuHue;
                        moved |= movesOnGpu(entry, want);
                        repainted++;
                    } else {
                        forget(e.getKey(), entry);
                    }
                } catch (Exception ex) {
                    // A reload cannot change a downloaded texture, so the store lets it keep its look.
                    if (entry.downloaded) forget(e.getKey(), entry);
                    else needReload++;
                }
            }
        } finally {
            repainting.remove();
            if (moved) paints.incrementAndGet();
        }
        return new Result(repainted, needReload);
    }

    /** Whether two lists of looks differ only in the speed of Chroma colors that move in both. */
    static boolean onlySpeedChanges(List<Appearance> before, List<Appearance> after) {
        if (before == null || before.size() != after.size()) return false;
        for (int i = 0; i < after.size(); i++) {
            Appearance a = before.get(i), b = after.get(i);
            if (a.chromaMoves() != b.chromaMoves()) return false;
            Chroma c = b.chroma().withOptions(a.chroma().speed(), b.chroma().saturation(), b.chroma().brightness());
            if (!a.equals(b.withEffects(b.bevel(), b.outline(), c))) return false;
        }
        return true;
    }

    /**
     * A number that changes after each record, drop and repaint of a texture whose Chroma colors move on the GPU, so that
     * an adapter knows when to read the {@link #chromaTexels} again (a downloaded skin does not change it).
     */
    public int paints() {
        return paints.get();
    }

    /**
     * Whether the GPU pass moves the Chroma colors of some kind of texture in a category: a key whose kept textures all
     * have GPU hue, where a key that also has a stand-alone texture does not count because the game draws that one (the
     * breaking stages since 1.15 are also unused sprites of the block atlas).
     */
    public boolean movingChroma(Category category) {
        int now = changes.get();
        if (movingChromaAt != now) {
            Map<TextureKey, Boolean> keys = new HashMap<TextureKey, Boolean>();
            for (Entry e : entries.values()) {
                Boolean before = keys.get(e.layout.key());
                keys.put(e.layout.key(), (before == null || before) && e.upload.gpuHue());
            }
            Set<Category> moving = EnumSet.noneOf(Category.class);
            for (Map.Entry<TextureKey, Boolean> k : keys.entrySet()) if (k.getValue()) moving.add(k.getKey().category());
            movingChroma = moving;
            movingChromaAt = now;
        }
        return movingChroma.contains(category);
    }

    /**
     * Puts the {@link ChromaTexels} of one frame of a texture into {@code out}, for the look that the texture has in the
     * game, when its upload has {@link Upload#gpuHue}.
     *
     * @param w the width of one frame in the atlas
     * @param h the height of one frame in the atlas
     * @return false when the texture is not kept, has no GPU hue or has no moving colors, which leaves {@code out} as it is
     */
    public boolean chromaTexels(String name, int w, int h, int[] out) {
        Entry entry = entries.get(name);
        if (entry == null || !entry.gpuHue) return false;
        List<Appearance> looks = entry.applied;
        return looks != null && entry.layout.chromaTexels(looks, entry.original, w, h, out);
    }

    /** Drops a texture that the game released, so that the store does not keep its pixels. */
    public synchronized void forget(String name) {
        Entry e = entries.get(name);
        if (e != null) forget(name, e);
        overBudget.remove(name);
    }

    /** Drops a texture that a reload replaced, unless the reload has recorded it again in the meantime. */
    private synchronized void forget(String name, Entry entry) {
        if (entries.remove(name, entry)) {
            changes.incrementAndGet();
            if (movesOnGpu(entry, entry.applied)) paints.incrementAndGet();
            (entry.downloaded ? downloadedBytes : bytes).addAndGet(-entry.bytes());
            if (count(entry, -1) || !entry.downloaded) version.incrementAndGet();
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
                List<TextureKey> keys = e.layout.catalogKeys();
                for (int i = 0; i < keys.size(); i++) {
                    TextureKey k = keys.get(i);
                    if (k.category() == category && !out.containsKey(k.id())) out.put(k.id(), e.averages[i]);
                }
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
