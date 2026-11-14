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
    /** The sheet of each tile that is in the category of its sheet. */
    private static final Map<TextureKey, TextureKey> SHEET_OF;

    static {
        Map<TextureKey, Layout> m = new HashMap<TextureKey, Layout>();
        Map<TextureKey, TextureKey> sheetOf = new HashMap<TextureKey, TextureKey>();
        for (Layout l : new Layout[]{particles(), paintings(), explosion()}) {
            m.put(l.key(), l);
            for (Layout.Tile t : l.tiles()) if (t.key().category() == l.key().category()) sheetOf.put(t.key(), l.key());
        }
        ALL = Collections.unmodifiableMap(m);
        SHEET_OF = Collections.unmodifiableMap(sheetOf);
    }

    private Sheets() {}

    /** The layout of a sheet, or null for any other texture. */
    static Layout get(TextureKey key) {
        return ALL.get(key);
    }

    static TextureKey sheetOf(TextureKey tile) {
        return SHEET_OF.get(tile);
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
        b.cells("effect_", 128, 8); // potion effects of mobs and splash potions
        b.cells("spell_", 144, 8); // witch and instant potions
        b.cells("spark_", 160, 8); // firework sparks
        for (int i = 0; i < 26; i++) b.cell("sga_" + (char) ('a' + i), 225 + i); // enchanting table glyphs
        return b.build("minecraft:textures/particle/particles.png");
    }

    /** The size and offset in pixels of a 256-pixel sheet are those of the 1.8.9 EntityPainting.EnumArt values. */
    private static Layout paintings() {
        Builder b = new Builder(Category.DECORATIONS, 16, 16);
        b.painting("kebab", 16, 16, 0, 0);
        b.painting("aztec", 16, 16, 16, 0);
        b.painting("alban", 16, 16, 32, 0);
        b.painting("aztec2", 16, 16, 48, 0);
        b.painting("bomb", 16, 16, 64, 0);
        b.painting("plant", 16, 16, 80, 0);
        b.painting("wasteland", 16, 16, 96, 0);
        b.painting("pool", 32, 16, 0, 32);
        b.painting("courbet", 32, 16, 32, 32);
        b.painting("sea", 32, 16, 64, 32);
        b.painting("sunset", 32, 16, 96, 32);
        b.painting("creebet", 32, 16, 128, 32);
        b.painting("wanderer", 16, 32, 0, 64);
        b.painting("graham", 16, 32, 16, 64);
        b.painting("match", 32, 32, 0, 128);
        b.painting("bust", 32, 32, 32, 128);
        b.painting("stage", 32, 32, 64, 128);
        b.painting("void", 32, 32, 96, 128);
        b.painting("skull_and_roses", 32, 32, 128, 128);
        b.painting("wither", 32, 32, 160, 128);
        b.painting("fighters", 64, 32, 0, 96);
        b.painting("pointer", 64, 64, 0, 192);
        b.painting("pigscene", 64, 64, 64, 192);
        b.painting("burning_skull", 64, 64, 128, 192);
        b.painting("skeleton", 64, 48, 192, 64);
        b.painting("donkey_kong", 64, 48, 192, 112);
        b.painting("back", 16, 16, 192, 0); // RenderPainting draws the back and the edges from this cell
        return b.build("minecraft:textures/painting/paintings_kristoffer_zetterstrand.png");
    }

    /** EntityLargeExplodeFX draws frame n from cell (n % 4, n / 4). */
    private static Layout explosion() {
        Builder b = new Builder(Category.PARTICLES, 4, 4);
        b.cells("explosion_", 0, 16);
        return b.build("minecraft:textures/entity/explosion.png");
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

        /** A painting from its size and offset in pixels of a 256-pixel sheet, which has 16 pixels in each cell. */
        void painting(String name, int width, int height, int u, int v) {
            add(category, name, u / 16, v / 16, width / 16, height / 16);
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
