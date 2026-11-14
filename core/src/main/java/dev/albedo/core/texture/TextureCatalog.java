package dev.albedo.core.texture;

import dev.albedo.core.Category;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The kept ids of each category of a {@link TextureStore}, with the store lock around each {@link #replace} and {@link #remove}. */
final class TextureCatalog {
    /** The number of kept textures with each catalog key, so that a downloaded texture changes the catalog only when its key comes or goes. */
    private final Map<TextureKey, Integer> keyCounts = new HashMap<TextureKey, Integer>();
    private final AtomicInteger version = new AtomicInteger();
    private final Stamped<Map<Category, Map<String, Integer>>> views = new Stamped<Map<Category, Map<String, Integer>>>();

    /** Counts {@code next} in place of {@code before}, which is null for a new name. */
    void replace(TextureStore.Entry before, TextureStore.Entry next) {
        boolean keysChanged = count(next, 1);
        if (before != null) keysChanged |= count(before, -1);
        // Many downloaded skins share one key, so a new skin changes the catalog only when it adds the key.
        boolean newColors = !next.downloaded && (before == null || !Arrays.equals(before.averages, next.averages));
        if (keysChanged || newColors) version.incrementAndGet();
    }

    void remove(TextureStore.Entry e) {
        if (count(e, -1) || !e.downloaded) version.incrementAndGet();
    }

    /**
     * Adds {@code delta} to the count of each catalog key of an entry.
     *
     * @return true when a key comes into the catalog or leaves it
     */
    private boolean count(TextureStore.Entry e, int delta) {
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

    /** Ids of {@code entries} for a category in name order, each with its original average color, for the client thread. */
    Map<String, Integer> view(Category category, Map<String, TextureStore.Entry> entries) {
        Map<Category, Map<String, Integer>> byCategory =
            views.get(version.get(), () -> new EnumMap<Category, Map<String, Integer>>(Category.class));
        Map<String, Integer> view = byCategory.get(category);
        if (view == null) {
            Map<String, Integer> out = new TreeMap<String, Integer>();
            // In name order, so that of the textures with one id the first name (the vanilla one) gives the color.
            for (TextureStore.Entry e : new TreeMap<String, TextureStore.Entry>(entries).values()) {
                List<TextureKey> keys = e.layout.catalogKeys();
                for (int i = 0; i < keys.size(); i++) {
                    TextureKey k = keys.get(i);
                    if (k.category() == category && !out.containsKey(k.id())) out.put(k.id(), e.averages[i]);
                }
            }
            view = Collections.unmodifiableMap(out);
            byCategory.put(category, view);
        }
        return view;
    }
}
