package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntPredicate;

/** Every loaded texture with a copy of its original pixels, safe for the worker threads that load textures on modern versions. */
public final class TextureStore {
    /** How an adapter writes new pixels into a loaded texture on the client thread. */
    public interface Upload {
        /**
         * @param pixels the new pixels, the same size as the original
         * @return false when a reload has replaced the texture
         */
        boolean upload(PixelBuffer pixels) throws Exception;

        /** Whether the upload writes into the GPU texture that the game already has, which is cheap enough to move the Chroma colors. */
        default boolean inPlace() {
            return false;
        }
    }

    /** The upload for a texture type from a different mod, which only a resource reload can change. */
    public static final Upload RELOAD_ONLY = pixels -> {
        throw new IllegalStateException("a reload-only texture has no upload");
    };

    /** {@code upload} as one that writes in place, so that {@link #animate} moves its Chroma colors. */
    public static Upload inPlace(final Upload upload) {
        return new Upload() {
            @Override public boolean upload(PixelBuffer pixels) throws Exception { return upload.upload(pixels); }
            @Override public boolean inPlace() { return true; }
        };
    }

    public static final class Result {
        public final int repainted;
        /** Textures whose new look needs a resource reload, because their upload failed or is {@link #RELOAD_ONLY}. */
        public final int needReload;

        Result(int repainted, int needReload) {
            this.repainted = repainted;
            this.needReload = needReload;
        }
    }

    /** What one {@link #animate} call did. */
    public static final class Step {
        /** The textures whose Chroma colors move. */
        public final int moving;
        public final int repainted;
        /** Textures whose upload failed, which keep their colors still from now on. */
        public final int failed;

        Step(int moving, int repainted, int failed) {
            this.moving = moving;
            this.repainted = repainted;
            this.failed = failed;
        }
    }

    /** A texture whose Chroma colors move, with the looks that the policy of the list gives it. */
    private static final class Moving {
        final String name;
        final List<Appearance> looks;

        Moving(String name, List<Appearance> looks) {
            this.name = name;
            this.looks = looks;
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
        /** The Chroma step of each applied look. */
        volatile int[] phases;
        /** True after an upload from {@link #animate} failed, so that the colors of this texture stay still. */
        volatile boolean still;

        Entry(Layout layout, PixelBuffer.Heap original, Upload upload, List<Appearance> applied, int[] phases, boolean downloaded) {
            this.layout = layout;
            this.original = original;
            this.upload = upload;
            this.averages = layout.averages(original);
            this.applied = applied;
            this.phases = phases;
            this.downloaded = downloaded;
        }

        long bytes() { return 4L * original.width() * original.height(); }

        /** The Chroma steps for {@code looks} at {@code timeMs}, all 0 for a texture whose colors cannot move. */
        int[] phasesAt(List<Appearance> looks, long timeMs) {
            return upload.inPlace() && !still ? Layout.phases(looks, timeMs) : new int[looks.size()];
        }
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

    /** Counts each new or dropped entry, so that {@link #animate} knows when to make its list again. */
    private final AtomicInteger entryChanges = new AtomicInteger();
    /** The textures whose Chroma colors move under {@link #movingPolicy}, which only the client thread uses. */
    private List<Moving> moving = Collections.emptyList();
    private Policy movingPolicy;
    private int movingChanges = -1;
    /** The next texture in {@link #moving} that {@link #animate} examines. */
    private int cursor;
    /** Textures that want moving colors under {@link #movingPolicy} but keep them still, because an upload failed. */
    private int stillCount;

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
     * @param timeMs the time that gives the Chroma steps
     * @return the Chroma step of each applied look, which the caller paints the image with, or null without looks
     */
    public int[] record(String name, Layout layout, PixelBuffer image, Upload upload, List<Appearance> applied, long timeMs) {
        int[] still = applied == null ? null : new int[applied.size()];
        if (layout.key().category() == Category.OTHER || upload == null) return still;
        boolean downloaded = TextureClassifier.isDownloaded(name);
        int[] phases = upload.inPlace() && applied != null ? Layout.phases(applied, timeMs) : still;
        Entry next = new Entry(layout, PixelBuffer.Heap.copyOf(image), upload, applied, phases, downloaded);
        synchronized (this) { // worker threads record at the same time on modern versions
            Entry before = entries.get(name);
            long delta = next.bytes() - (before == null ? 0 : before.bytes());
            if (downloaded) {
                downloadedBytes.addAndGet(delta);
            } else {
                if (bytes.get() + delta > budget) {
                    overBudget.add(name);
                    return still;
                }
                overBudget.remove(name);
                bytes.addAndGet(delta);
            }
            entries.put(name, next);
            entryChanges.incrementAndGet();
            boolean keysChanged = count(next, 1);
            if (before != null) keysChanged |= count(before, -1);
            // Many downloaded skins share one key, so a new skin changes the catalog only when it adds the key.
            boolean newColors = !downloaded && (before == null || !Arrays.equals(before.averages, next.averages));
            if (keysChanged || newColors) version.incrementAndGet();
        }
        return phases;
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

    /** The bytes of original pixels of the downloaded skins, capes and elytra, which do not count toward the budget. */
    public long downloadedBytes() {
        return downloadedBytes.get();
    }

    /** Makes the next {@link #repaint} paint this texture, whose look in the game is not known. */
    public void invalidate(String name) {
        Entry e = entries.get(name);
        if (e != null) e.applied = null;
    }

    /**
     * Gives each kept texture the look {@code policy} wants, where a texture whose looks did not change keeps its
     * Chroma step for {@link #animate}.
     *
     * @param timeMs the time that gives the Chroma steps
     */
    public Result repaint(Policy policy, long timeMs) {
        int repainted = 0, needReload = 0;
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
                try {
                    PixelBuffer.Heap pixels = entry.original.copy();
                    int[] phases = entry.phasesAt(want, timeMs);
                    entry.layout.paint(want, phases, pixels);
                    if (entry.upload.upload(pixels)) {
                        entry.phases = phases;
                        entry.applied = want;
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
        }
        return new Result(repainted, needReload);
    }

    /**
     * Repaints, from where the last call stopped, each texture with moving Chroma colors whose step at {@code timeMs}
     * is not the one it shows, on the client thread after {@link #repaint} with the same policy.
     *
     * @param budget tells from the number of textures repainted in this call whether one more can be repainted
     */
    public Step animate(Policy policy, long timeMs, IntPredicate budget) {
        if (policy != movingPolicy || entryChanges.get() != movingChanges) findMoving(policy);
        int repainted = 0, failed = 0;
        repainting.set(Boolean.TRUE);
        try {
            for (int seen = 0, n = moving.size(); seen < n && !moving.isEmpty() && budget.test(repainted); seen++) {
                if (cursor >= moving.size()) cursor = 0;
                Moving m = moving.get(cursor);
                Entry entry = entries.get(m.name);
                // A texture with an unknown or older look waits for the repaint that the next tick does.
                int[] want = Layout.phases(m.looks, timeMs);
                if (entry == null || !m.looks.equals(entry.applied) || Arrays.equals(want, entry.phases)) {
                    cursor++;
                    continue;
                }
                try {
                    PixelBuffer.Heap pixels = entry.original.copy();
                    entry.layout.paint(m.looks, want, pixels);
                    if (entry.upload.upload(pixels)) {
                        entry.phases = want;
                        repainted++;
                        cursor++;
                    } else {
                        forget(m.name, entry);
                        moving.remove(cursor);
                    }
                } catch (Exception ex) {
                    // Only the colors stop, so that an animation never causes a resource reload.
                    entry.still = true;
                    failed++;
                    stillCount++;
                    moving.remove(cursor);
                }
            }
        } finally {
            repainting.remove();
        }
        return new Step(moving.size(), repainted, failed);
    }

    /** Textures that want moving Chroma colors under the last policy of {@link #animate}, but keep them still because an upload failed. */
    public int stillCount() {
        return stillCount;
    }

    /** Makes the list of textures whose Chroma colors move under {@code policy}. */
    private void findMoving(Policy policy) {
        movingChanges = entryChanges.get();
        movingPolicy = policy;
        List<Moving> out = new ArrayList<Moving>();
        int still = 0;
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (!entry.upload.inPlace()) continue;
            List<Appearance> looks = entry.layout.looks(policy);
            boolean moves = false;
            for (Appearance a : looks) moves |= a.chromaMoves();
            if (!moves) continue;
            if (entry.still) still++;
            else out.add(new Moving(e.getKey(), looks));
        }
        moving = out;
        stillCount = still;
        if (cursor >= out.size()) cursor = 0;
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
            entryChanges.incrementAndGet();
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
