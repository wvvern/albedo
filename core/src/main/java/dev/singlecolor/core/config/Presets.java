package dev.singlecolor.core.config;

import dev.singlecolor.core.Category;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in profiles written to the config file on first launch. */
public final class Presets {
    private Presets() {}

    public static Map<String, Profile> all() {
        Map<String, Profile> m = new LinkedHashMap<String, Profile>();
        add(m, quake(true));
        add(m, quake(false));
        add(m, minimal());
        return m;
    }

    private static void add(Map<String, Profile> m, Profile p) {
        m.put(p.name(), p);
    }

    /** Everything static is one flat color; anything that moves keeps its texture. */
    static Profile quake(boolean white) {
        int block = white ? 0xFFFFFFFF : 0xFF141414;
        int fluid = white ? 0xFFD0D0D0 : 0xFF0A0A0A;
        int sky = white ? 0xFFFFFFFF : 0xFF000000;
        Map<Category, CategorySettings> c = new EnumMap<Category, CategorySettings>(Category.class);
        c.put(Category.BLOCKS, flat(block, true));
        c.put(Category.BLOCK_ENTITIES, flat(block, false));
        c.put(Category.FLUIDS, flat(fluid, true));
        c.put(Category.SKY, flat(sky, false));
        c.put(Category.CLOUDS, hidden());
        c.put(Category.WEATHER, hidden());
        return new Profile(white ? "quake-white" : "quake-black", c);
    }

    /** Each block becomes its own average color: detail gone, still readable. */
    static Profile minimal() {
        Map<Category, CategorySettings> c = new EnumMap<Category, CategorySettings>(Category.class);
        CategorySettings avg = new CategorySettings(Appearance.of(Appearance.Mode.AVERAGE), false, Collections.<Rule>emptyList());
        c.put(Category.BLOCKS, avg);
        c.put(Category.BLOCK_ENTITIES, avg);
        c.put(Category.FLUIDS, avg);
        c.put(Category.CLOUDS, hidden());
        return new Profile("minimal", c);
    }

    private static CategorySettings flat(int color, boolean removeTint) {
        return new CategorySettings(Appearance.solid(color), removeTint, Collections.<Rule>emptyList());
    }

    private static CategorySettings hidden() {
        return new CategorySettings(Appearance.of(Appearance.Mode.HIDDEN), false, Collections.<Rule>emptyList());
    }
}
