package dev.albedo.core.config;

import dev.albedo.core.Category;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The built-in profiles, which come from code and not from the config file so that they cannot change and an update
 * can improve them.
 */
public final class Presets {
    private static final Map<String, Profile> ALL;

    static {
        Map<String, Profile> m = new LinkedHashMap<String, Profile>();
        for (Profile p : new Profile[]{quake(true), quake(false), minimal(), pastel()}) m.put(p.name(), p);
        ALL = Collections.unmodifiableMap(m);
    }

    private Presets() {}

    /** In menu order. */
    public static Map<String, Profile> all() {
        return ALL;
    }

    /** Everything static is one flat color, evenly lit, without plants; anything that moves keeps its texture. */
    static Profile quake(boolean white) {
        int block = white ? 0xFFFFFFFF : 0xFF141414;
        int fluid = white ? 0xFFD0D0D0 : 0xFF0A0A0A;
        int sky = white ? 0xFFFFFFFF : 0xFF000000;
        // Signs, banners and maps are toned, not lost: their text and patterns stay readable on them.
        int decoration = white ? 0xFFD4D4D4 : 0xFF6A6A6A;
        Map<Category, CategorySettings> c = new EnumMap<Category, CategorySettings>(Category.class);
        // Solid leaves hide the darker back faces behind their holes.
        Rule solidLeaves = new Rule("Leaves", Collections.singletonList("*leaves*"),
                Appearance.ORIGINAL.withAlpha(Appearance.Alpha.OPAQUE), EnumSet.of(Appearance.Field.ALPHA));
        c.put(Category.BLOCKS, new CategorySettings(Appearance.solid(block), true, Collections.singletonList(solidLeaves)));
        c.put(Category.BLOCK_ENTITIES, flat(block, false));
        c.put(Category.DECORATIONS, flat(decoration, false));
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
        CategorySettings avg = plain(Appearance.of(Appearance.Mode.AVERAGE), false);
        c.put(Category.BLOCKS, avg);
        c.put(Category.BLOCK_ENTITIES, avg);
        c.put(Category.DECORATIONS, avg);
        c.put(Category.FLUIDS, avg);
        c.put(Category.CLOUDS, hidden());
        return new Profile("minimal", c, Profile.defaultToggles());
    }

    /** A soft world where each kind of block has its own pastel color that drifts slowly and mobs and players keep their textures. */
    static Profile pastel() {
        Map<Category, CategorySettings> c = new EnumMap<Category, CategorySettings>(Category.class);
        // Half-strength Chroma keeps the colors soft, and one turn takes about 13 s.
        Chroma pastel = new Chroma(55, 54, Chroma.DEFAULT_SATURATION, Chroma.DEFAULT_BRIGHTNESS);
        Appearance blocks = new Appearance(Appearance.Mode.RECOLOR, 0xFFF1E6F6, Appearance.Alpha.KEEP, 60, 0, 35, 30, 0, pastel);
        Rule solidLeaves = new Rule("Leaves", Collections.singletonList("*leaves*"),
                blocks.withAlpha(Appearance.Alpha.OPAQUE), EnumSet.of(Appearance.Field.ALPHA));
        c.put(Category.BLOCKS, new CategorySettings(blocks, true, Collections.singletonList(solidLeaves)));
        // Only the 1.21 versions keep the chests in an atlas that the GPU pass can move, so the block entities get no Chroma.
        c.put(Category.BLOCK_ENTITIES, plain(blocks.withEffects(0, 0, Chroma.OFF), false));
        c.put(Category.DECORATIONS, plain(new Appearance(Appearance.Mode.RECOLOR, 0xFFE8DCEF, Appearance.Alpha.KEEP, 85, 40, 30), false));
        c.put(Category.FLUIDS, plain(new Appearance(Appearance.Mode.RECOLOR, 0xFFA6D8EE, Appearance.Alpha.KEEP, 70, 0, 0), true));
        c.put(Category.SKY, flat(0xFFF9D4E6, false));
        Set<Toggle> on = Profile.defaultToggles();
        on.add(Toggle.FULLBRIGHT);
        return new Profile("pastel", c, on);
    }

    /** A category with one look and no groups. */
    private static CategorySettings plain(Appearance look, boolean removeTint) {
        return new CategorySettings(look, removeTint, Collections.<Rule>emptyList());
    }

    private static CategorySettings flat(int color, boolean removeTint) {
        return plain(Appearance.solid(color), removeTint);
    }

    private static CategorySettings hidden() {
        return plain(Appearance.of(Appearance.Mode.HIDDEN), false);
    }
}
