package dev.albedo.core.config;

import dev.albedo.core.Category;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Built-in profiles written to the config file on first launch. */
public final class Presets {
    private Presets() {}

    public static Map<String, Profile> all() {
        Map<String, Profile> m = new LinkedHashMap<String, Profile>();
        for (Profile p : new Profile[]{quake(true), quake(false), minimal()}) m.put(p.name(), p);
        return m;
    }

    /** Everything static is one flat color, evenly lit, without plants; anything that moves keeps its texture. */
    static Profile quake(boolean white) {
        int block = white ? 0xFFFFFFFF : 0xFF141414;
        int fluid = white ? 0xFFD0D0D0 : 0xFF0A0A0A;
        int sky = white ? 0xFFFFFFFF : 0xFF000000;
        Map<Category, CategorySettings> c = new EnumMap<Category, CategorySettings>(Category.class);
        // Solid leaves: through their holes you'd otherwise see darker back faces, which is exactly the noise to remove.
        Rule solidLeaves = new Rule("Leaves", Collections.singletonList("*leaves*"),
                new Appearance(Appearance.Mode.SOLID, block, null, Appearance.Alpha.OPAQUE), false);
        c.put(Category.BLOCKS, new CategorySettings(Appearance.solid(block), true, Collections.singletonList(solidLeaves)));
        c.put(Category.BLOCK_ENTITIES, flat(block, false));
        c.put(Category.FLUIDS, flat(fluid, true));
        c.put(Category.SKY, flat(sky, false));
        c.put(Category.CLOUDS, hidden());
        c.put(Category.WEATHER, hidden());
        // Shading stays on: with everything one color, it's what keeps block shapes readable.
        Set<Toggle> on = Profile.defaultToggles();
        on.add(Toggle.FULLBRIGHT);
        on.remove(Toggle.FOLIAGE);
        return new Profile(white ? "quake-white" : "quake-black", c, on);
    }

    /** Each block becomes its own average color: detail gone, still readable. */
    static Profile minimal() {
        Map<Category, CategorySettings> c = new EnumMap<Category, CategorySettings>(Category.class);
        CategorySettings avg = new CategorySettings(Appearance.of(Appearance.Mode.AVERAGE), false, Collections.<Rule>emptyList());
        c.put(Category.BLOCKS, avg);
        c.put(Category.BLOCK_ENTITIES, avg);
        c.put(Category.FLUIDS, avg);
        c.put(Category.CLOUDS, hidden());
        return new Profile("minimal", c, Profile.defaultToggles());
    }

    private static CategorySettings flat(int color, boolean removeTint) {
        return new CategorySettings(Appearance.solid(color), removeTint, Collections.<Rule>emptyList());
    }

    private static CategorySettings hidden() {
        return new CategorySettings(Appearance.of(Appearance.Mode.HIDDEN), false, Collections.<Rule>emptyList());
    }
}
