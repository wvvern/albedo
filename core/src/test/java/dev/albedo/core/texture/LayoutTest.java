package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Rule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {
    static final String PARTICLES = "minecraft:textures/particle/particles.png";
    private static final String PAINTINGS = "minecraft:textures/painting/paintings_kristoffer_zetterstrand.png";
    private static final String EXPLOSION = "minecraft:textures/entity/explosion.png";
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF0000;

    static Layout sheet(String location) {
        Layout layout = Layout.of(TextureClassifier.classify(location));
        assertFalse(layout.tiles().isEmpty(), location + " is a sheet");
        return layout;
    }

    static Layout.Tile tile(Layout layout, String id) {
        for (Layout.Tile t : layout.tiles()) if (t.key().id().equals("minecraft:" + id)) return t;
        throw new AssertionError("no tile " + id);
    }

    /** A sheet with one opaque color for each cell of a 16 x 16 grid, so that each cell has its own average. */
    static PixelBuffer.Heap cells(int width, int height) {
        PixelBuffer.Heap img = new PixelBuffer.Heap(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) img.setArgb(x, y, cellColor(x * 16 / width, y * 16 / height));
        }
        return img;
    }

    static int cellColor(int col, int row) {
        return 0xFF000000 | (col * 16 << 16) | (row * 16 << 8) | 0x40;
    }

    static Policy particles(Appearance look, Rule... groups) {
        Config c = Config.defaults();
        CategorySettings cs = new CategorySettings(look, false, Arrays.asList(groups));
        return Policy.of(c.withProfile(c.activeProfile().with(Category.PARTICLES, cs)));
    }

    static Rule group(String pattern, Appearance look) {
        return new Rule(pattern, Collections.singletonList(pattern), look, false);
    }

    /** The color at the center of a cell of the 16 x 16 grid. */
    private static int at(PixelBuffer img, int col, int row) {
        return img.getArgb((col * 2 + 1) * img.width() / 32, (row * 2 + 1) * img.height() / 32);
    }

    @Test
    void sheetTilesFitTheGridWithoutOverlap() {
        for (String location : new String[]{PARTICLES, PAINTINGS, EXPLOSION}) {
            Layout layout = sheet(location);
            Set<String> ids = new HashSet<String>();
            boolean[][] used = new boolean[16][16];
            for (Layout.Tile t : layout.tiles()) {
                assertTrue(ids.add(t.key().category() + "/" + t.key().id()), "unique id " + t.key());
                int[] b = layout.bounds(t, 16, 16);
                for (int y = b[1]; y < b[3]; y++) {
                    for (int x = b[0]; x < b[2]; x++) {
                        assertFalse(used[y][x], "overlap at " + x + "," + y + " of " + t.key());
                        used[y][x] = true;
                    }
                }
            }
        }
    }

    @Test
    void paintingTilesHaveTheNamesOfLaterVersions() {
        Layout layout = sheet(PAINTINGS);
        assertEquals(Category.DECORATIONS, layout.key().category());
        // The file names in textures/painting/ of 1.21.1 that 1.8.9 has.
        List<String> later = Arrays.asList("alban", "aztec", "aztec2", "back", "bomb", "burning_skull", "bust", "courbet", "creebet",
                "donkey_kong", "fighters", "graham", "kebab", "match", "pigscene", "plant", "pointer", "pool", "sea", "skeleton",
                "skull_and_roses", "stage", "sunset", "void", "wanderer", "wasteland", "wither");
        Set<String> ids = new HashSet<String>();
        for (Layout.Tile t : layout.tiles()) {
            assertEquals(Category.DECORATIONS, t.key().category());
            ids.add(t.key().id().substring("minecraft:".length()));
        }
        assertEquals(new HashSet<String>(later), ids);
        assertArrayEquals(new int[]{12, 4, 16, 7}, layout.bounds(tile(layout, "skeleton"), 16, 16));
        assertArrayEquals(new int[]{12, 0, 13, 1}, layout.bounds(tile(layout, "back"), 16, 16));
    }

    @Test
    void explosionFramesAreParticles() {
        Layout layout = sheet(EXPLOSION);
        assertEquals(Category.ENTITIES, layout.key().category());
        assertEquals(16, layout.tiles().size());
        Layout.Tile frame = tile(layout, "explosion_5");
        assertEquals(Category.PARTICLES, frame.key().category(), "later versions have the explosion in the particle atlas");
        assertArrayEquals(new int[]{32, 32, 64, 64}, layout.bounds(frame, 128, 128));
    }

    @Test
    void particleTilesHaveTheCellsOfThe189Classes() {
        Layout layout = sheet(PARTICLES);
        assertEquals(Category.PARTICLES, layout.key().category());
        assertArrayEquals(new int[]{0, 0, 1, 1}, layout.bounds(tile(layout, "generic_0"), 16, 16));
        assertArrayEquals(new int[]{3, 1, 4, 2}, layout.bounds(tile(layout, "splash_0"), 16, 16));
        assertArrayEquals(new int[]{0, 3, 1, 4}, layout.bounds(tile(layout, "flame"), 16, 16));
        assertArrayEquals(new int[]{4, 2, 8, 6}, layout.bounds(tile(layout, "flash"), 16, 16));
        assertArrayEquals(new int[]{1, 7, 2, 8}, layout.bounds(tile(layout, "drip_hang"), 16, 16), "the hanging drop draws cell 113");
        assertArrayEquals(new int[]{0, 7, 1, 8}, layout.bounds(tile(layout, "drip_fall"), 16, 16));
        assertArrayEquals(new int[]{0, 8, 1, 9}, layout.bounds(tile(layout, "effect_0"), 16, 16));
        assertArrayEquals(new int[]{0, 9, 1, 10}, layout.bounds(tile(layout, "spell_0"), 16, 16));
        assertArrayEquals(new int[]{7, 10, 8, 11}, layout.bounds(tile(layout, "spark_7"), 16, 16));
        assertArrayEquals(new int[]{1, 14, 2, 15}, layout.bounds(tile(layout, "sga_a"), 16, 16));
        assertArrayEquals(new int[]{10, 15, 11, 16}, layout.bounds(tile(layout, "sga_z"), 16, 16));
        Layout.Tile hook = tile(layout, "fishing_hook");
        assertEquals(Category.ENTITIES, hook.key().category(), "the fishing hook is an entity texture on later versions");
        assertArrayEquals(new int[]{1, 2, 2, 3}, layout.bounds(hook, 16, 16));
    }

    @Test
    void aGroupChangesOnlyItsTileAtAnyResolution() {
        for (int size : new int[]{128, 256}) {
            Layout layout = sheet(PARTICLES);
            PixelBuffer.Heap img = cells(size, size);
            Policy policy = particles(Appearance.solid(WHITE), group("flame", Appearance.solid(RED)));
            assertTrue(layout.paint(layout.looks(policy), img, null));
            assertEquals(RED, at(img, 0, 3), "flame");
            assertEquals(WHITE, at(img, 1, 3), "lava takes the category look");
            assertEquals(WHITE, at(img, 0, 1), "a cell outside the tiles takes the look of the sheet");
        }
    }

    @Test
    void aTileWithTheOriginalLookKeepsItsPixels() {
        Layout layout = sheet(PARTICLES);
        PixelBuffer.Heap img = cells(128, 128);
        Policy policy = particles(Appearance.of(Appearance.Mode.HIDDEN), group("heart", Appearance.ORIGINAL));
        layout.paint(layout.looks(policy), img, null);
        assertEquals(cellColor(0, 5), at(img, 0, 5));
        assertEquals(0, at(img, 1, 5) >>> 24, "the angry villager is hidden");
    }

    @Test
    void eachTileGetsItsOwnAverageAndUserImage() {
        Layout layout = sheet(PARTICLES);
        PixelBuffer.Heap img = cells(128, 128);
        PixelBuffer blue = new PixelBuffer.Heap(1, 1, new int[]{0xFF0000FF});
        Appearance image = new Appearance(Appearance.Mode.TEXTURE, WHITE, "blue.png", Appearance.Alpha.KEEP);
        Policy policy = particles(Appearance.of(Appearance.Mode.AVERAGE), group("note", image));
        layout.paint(layout.looks(policy), img, name -> name.equals("blue.png") ? blue : null);
        assertEquals(cellColor(0, 3), at(img, 0, 3), "the average of one uniform cell is its color");
        assertEquals(0xFF0000FF, at(img, 0, 4), "the user image fills the note tile");
        int[] averages = layout.averages(cells(128, 128));
        assertEquals(layout.catalogKeys().size(), averages.length);
        assertEquals(cellColor(0, 3), averages[layout.catalogKeys().indexOf(tile(layout, "flame").key())]);
    }

    @Test
    void anyImageSizeWorks() {
        Layout layout = sheet(PARTICLES);
        Policy policy = particles(Appearance.solid(WHITE), group("flame", Appearance.solid(RED)));
        for (int[] size : new int[][]{{1, 1}, {8, 8}, {100, 60}, {128, 256}}) {
            PixelBuffer.Heap img = cells(size[0], size[1]);
            layout.paint(layout.looks(policy), img, null);
            assertNotNull(layout.averages(img));
        }
        PixelBuffer.Heap tall = cells(128, 256);
        layout.paint(layout.looks(policy), tall, null);
        assertEquals(RED, at(tall, 0, 3), "a sheet that is not square keeps the same grid");
    }

    @Test
    void aGroupForTheWholeSheetCoversTheTilesThatNoGroupNames() {
        Layout layout = sheet(PARTICLES);
        PixelBuffer.Heap img = cells(128, 128);
        Policy policy = particles(Appearance.solid(WHITE), group("flame", Appearance.solid(0xFF00FF00)), group("particles", Appearance.solid(RED)));
        layout.paint(layout.looks(policy), img, null);
        assertEquals(0xFF00FF00, at(img, 0, 3), "the group of the tile wins");
        assertEquals(RED, at(img, 1, 3), "lava takes the group of the sheet");
        assertEquals(RED, at(img, 0, 1), "so do the cells outside the tiles");
        assertEquals(cellColor(1, 2), at(img, 1, 2), "the fishing hook is in another category");
    }

    @Test
    void aSheetThatItsTilesCoverHasNoLookOfItsOwn() {
        Layout layout = sheet(EXPLOSION);
        Config c = Config.defaults();
        CategorySettings red = new CategorySettings(Appearance.solid(RED), false, Collections.<Rule>emptyList());
        Policy mobs = Policy.of(c.withProfile(c.activeProfile().with(Category.ENTITIES, red)));
        assertEquals(Appearance.ORIGINAL, layout.looks(mobs).get(0));
        assertFalse(layout.paint(layout.looks(mobs), cells(128, 128), null), "the Entities look does not reach the explosion");
    }

    @Test
    void withTheModOffNothingChanges() {
        Layout layout = sheet(PARTICLES);
        PixelBuffer.Heap img = cells(128, 128);
        assertFalse(layout.paint(layout.looks(Policy.DISABLED), img, null));
        assertArrayEquals(cells(128, 128).argb(), img.argb());
    }

    @Test
    void aPlainTexturePaintsAsOneImage() {
        Layout layout = Layout.of(TextureClassifier.classify("minecraft:block/stone"));
        assertTrue(layout.tiles().isEmpty());
        assertEquals(1, layout.catalogKeys().size());
        PixelBuffer.Heap img = cells(16, 16), expected = cells(16, 16);
        Appearance white = Appearance.solid(WHITE);
        List<Appearance> looks = Collections.singletonList(white);
        layout.paint(looks, img, null);
        PixelTransformer.apply(white, expected, null);
        assertArrayEquals(expected.argb(), img.argb());
    }
}
