package dev.albedo.core.texture;

import dev.albedo.core.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 1.8.9 textures that hold several things which later versions split into one texture each, with the tiles named
 * as the later textures, so that one group pattern works on every version.
 */
final class Sheets {
    private static final Map<TextureKey, Layout> ALL;

    static {
        Map<TextureKey, Layout> m = new HashMap<TextureKey, Layout>();
        for (Layout l : new Layout[]{particles()}) m.put(l.key(), l);
        ALL = Collections.unmodifiableMap(m);
    }

    private Sheets() {}

    /** The layout of a sheet, or null for any other texture. */
    static Layout get(TextureKey key) {
        return ALL.get(key);
    }

    /** The cell indices (row * 16 + column) are the arguments of setParticleTextureIndex in the 1.8.9 particle classes. */
    private static Layout particles() {
        Builder b = new Builder(Category.PARTICLES, 16, 16);
        b.cells("generic_", 0, 8); // smoke, cloud, dust, explosion, portal, suspended
        b.cells("splash_", 19, 4); // rain, splash, fish wake
        b.cell("bubble", 32);
        b.add(Category.ENTITIES, "fishing_hook", 1, 2, 1, 1); // RenderFish
        b.add(Category.PARTICLES, "flash", 4, 2, 4, 4); // firework flash
        b.cell("flame", 48);
        b.cell("lava", 49);
        b.cell("note", 64);
        b.cell("critical_hit", 65);
        b.cell("enchanted_hit", 66);
        b.cell("heart", 80);
        b.cell("angry", 81);
        b.cell("glint", 82); // happy villager
        // The hanging drop draws cell 113 and the falling drop cell 112, so the names follow the role of the drop.
        b.cell("drip_fall", 112);
        b.cell("drip_hang", 113);
        b.cell("drip_land", 114);
        b.cells("effect_", 128, 8); // potion effects of mobs
        b.cells("spell_", 144, 8); // witch and splash potions
        b.cells("spark_", 160, 8); // firework sparks
        for (int i = 0; i < 26; i++) b.cell("sga_" + (char) ('a' + i), 225 + i); // enchanting table glyphs
        return b.build("minecraft:textures/particle/particles.png");
    }

    /** Collects the tiles of one sheet in cells of a grid. */
    private static final class Builder {
        private final Category category;
        private final int cols;
        private final int rows;
        private final List<Layout.Tile> tiles = new ArrayList<Layout.Tile>();

        Builder(Category category, int cols, int rows) {
            this.category = category;
            this.cols = cols;
            this.rows = rows;
        }

        void add(Category c, String name, int x, int y, int w, int h) {
            tiles.add(new Layout.Tile(new TextureKey(c, "minecraft:" + name), x, y, w, h));
        }

        void cell(String name, int index) {
            add(category, name, index % cols, index / cols, 1, 1);
        }

        /** The cells {@code first} to {@code first + count - 1}, named {@code prefix0} and up. */
        void cells(String prefix, int first, int count) {
            for (int i = 0; i < count; i++) cell(prefix + i, first + i);
        }

        Layout build(String location) {
            return new Layout(TextureClassifier.classify(location), cols, rows, tiles);
        }
    }
}
