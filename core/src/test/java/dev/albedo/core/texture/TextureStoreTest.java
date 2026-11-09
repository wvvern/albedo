package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Rule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureStoreTest {
    private static final int GREEN = 0xFF2E7D32;

    /** Records each upload; can act as a texture that is gone or broken. */
    static final class FakeUpload implements TextureStore.Upload {
        final List<int[]> uploads = new ArrayList<int[]>();
        boolean gone;
        boolean broken;

        @Override
        public boolean upload(PixelBuffer pixels) throws Exception {
            if (broken) throw new IllegalStateException("broken");
            if (gone) return false;
            uploads.add(((PixelBuffer.Heap) pixels).argb().clone());
            return true;
        }
    }

    private static PixelBuffer.Heap image() {
        return new PixelBuffer.Heap(2, 1, new int[]{GREEN, 0x00000000});
    }

    private static Policy blocks(Appearance look, Rule... groups) {
        return LayoutTest.policy(Category.BLOCKS, look, groups);
    }

    private static void record(TextureStore store, String id, TextureStore.Upload upload, Policy policy) {
        recordNamed(store, "minecraft:block/" + id, upload, policy);
    }

    private static void recordNamed(TextureStore store, String name, TextureStore.Upload upload, Policy policy) {
        Layout layout = Layout.of(name);
        store.record(name, layout, image(), upload, layout.looks(policy));
    }

    @Test
    void repaintsOnlyTexturesWhoseLookChanged() {
        TextureStore store = new TextureStore();
        Policy white = blocks(Appearance.solid(0xFFFFFFFF));
        FakeUpload stone = new FakeUpload(), dirt = new FakeUpload();
        record(store, "stone", stone, white);
        record(store, "dirt", dirt, white);

        // A new group for dirt repaints dirt only, from its original pixels; transparent pixels stay transparent.
        Rule red = new Rule("Ground", Collections.singletonList("dirt"), Appearance.solid(0xFFFF0000), false);
        Policy withGroup = blocks(Appearance.solid(0xFFFFFFFF), red);
        TextureStore.Result r = store.repaint(withGroup);
        assertEquals(1, r.repainted);
        assertEquals(0, stone.uploads.size());
        assertArrayEquals(new int[]{0xFFFF0000, 0x00FF0000}, dirt.uploads.get(0));

        // The same policy again does nothing; turning the mod off gives back the originals.
        assertEquals(0, store.repaint(withGroup).repainted);
        assertEquals(2, store.repaint(Policy.DISABLED).repainted);
        assertArrayEquals(image().argb(), stone.uploads.get(0));
        assertEquals(GREEN, (int) store.catalog(Category.BLOCKS).get("minecraft:stone") | 0, "catalog has the original average");
    }

    @Test
    void goneTexturesAreDroppedAndBrokenOnesReported() {
        TextureStore store = new TextureStore();
        FakeUpload gone = new FakeUpload(), broken = new FakeUpload();
        record(store, "stone", gone, Policy.DISABLED);
        record(store, "dirt", broken, Policy.DISABLED);
        gone.gone = true;
        broken.broken = true;
        TextureStore.Result r = store.repaint(blocks(Appearance.solid(0xFFFFFFFF)));
        assertEquals(0, r.repainted);
        assertEquals(1, r.needReload);
        assertFalse(store.catalog(Category.BLOCKS).containsKey("minecraft:stone"), "a gone texture leaves the store");
    }

    @Test
    void aNullUploadKeepsTheTextureThatIsThere() {
        TextureStore store = new TextureStore();
        FakeUpload stone = new FakeUpload();
        record(store, "stone", stone, Policy.DISABLED);
        record(store, "stone", null, Policy.DISABLED);
        assertEquals(1, store.repaint(blocks(Appearance.solid(0xFFFFFFFF))).repainted);
        assertEquals(1, stone.uploads.size());
        assertTrue(store.complete());
    }

    @Test
    void aReloadOnlyTextureNeedsAReloadOnlyWhenItsLookChanges() {
        TextureStore store = new TextureStore();
        FakeUpload stone = new FakeUpload();
        record(store, "stone", stone, Policy.DISABLED);
        String modded = "minecraft:textures/entity/modded.png";
        recordNamed(store, modded, TextureStore.RELOAD_ONLY, Policy.DISABLED);
        assertTrue(store.complete());
        assertTrue(store.catalog(Category.ENTITIES).containsKey(TextureClassifier.classify(modded).id()), "the texture list shows it");

        TextureStore.Result blocksOnly = store.repaint(blocks(Appearance.solid(0xFFFFFFFF)));
        assertEquals(1, blocksOnly.repainted);
        assertEquals(0, blocksOnly.needReload, "a change to other textures repaints them");

        Config c = Config.defaults();
        CategorySettings green = new CategorySettings(Appearance.solid(0xFF00FF00), false, Collections.<Rule>emptyList());
        Policy mobs = Policy.of(c.withProfile(c.activeProfile().with(Category.ENTITIES, green)));
        assertEquals(1, store.repaint(mobs).needReload);
    }

    @Test
    void anInvalidatedTextureIsRepaintedWithTheSameLook() {
        TextureStore store = new TextureStore();
        FakeUpload stone = new FakeUpload();
        record(store, "stone", stone, Policy.DISABLED);
        store.invalidate("minecraft:block/stone");
        assertEquals(1, store.repaint(Policy.DISABLED).repainted);
    }

    @Test
    void texturesWithOneKeyAreAllRepainted() {
        // OptiFine connected textures of a block get the key of the block.
        TextureStore store = new TextureStore();
        FakeUpload vanilla = new FakeUpload(), tile = new FakeUpload();
        recordNamed(store, "minecraft:blocks/bookshelf", vanilla, Policy.DISABLED);
        recordNamed(store, "minecraft:optifine/ctm/default/12", tile, Policy.DISABLED);
        assertEquals(TextureClassifier.classify("minecraft:blocks/bookshelf"), TextureClassifier.classify("minecraft:optifine/ctm/default/12"));
        assertEquals(2, store.repaint(blocks(Appearance.solid(0xFFFFFFFF))).repainted);
        assertEquals(1, vanilla.uploads.size());
        assertEquals(1, tile.uploads.size());
        assertEquals(1, store.catalog(Category.BLOCKS).size());
    }

    @Test
    void overBudgetTheStoreIsIncomplete() {
        TextureStore store = new TextureStore(8);
        record(store, "stone", new FakeUpload(), Policy.DISABLED);
        assertTrue(store.complete());
        record(store, "dirt", new FakeUpload(), Policy.DISABLED);
        assertFalse(store.complete());
        assertEquals(1, store.catalog(Category.BLOCKS).size());
    }

    private static void recordSheet(TextureStore store, PixelBuffer.Heap img, TextureStore.Upload upload, Policy policy) {
        Layout layout = Layout.of(LayoutTest.PARTICLES);
        store.record(LayoutTest.PARTICLES, layout, img, upload, layout.looks(policy));
    }

    @Test
    void theParticleSheetListsItsTilesInTheirOwnCategories() {
        TextureStore store = new TextureStore();
        recordSheet(store, LayoutTest.cells(128, 128), new FakeUpload(), Policy.DISABLED);
        assertEquals(LayoutTest.cellColor(0, 3), (int) store.catalog(Category.PARTICLES).get("minecraft:flame"));
        assertTrue(store.catalog(Category.PARTICLES).containsKey("minecraft:sga_z"));
        assertFalse(store.catalog(Category.PARTICLES).containsKey("minecraft:particles"), "the sheet shows as its tiles");
        assertFalse(store.catalog(Category.PARTICLES).containsKey("minecraft:fishing_hook"));
        assertTrue(store.catalog(Category.ENTITIES).containsKey("minecraft:fishing_hook"));

        // A resource pack with other pixels gives the texture list new colors.
        PixelBuffer.Heap red = LayoutTest.cells(128, 128);
        red.setArgb(0, 24, 0xFFFF0000);
        recordSheet(store, red, new FakeUpload(), Policy.DISABLED);
        assertTrue(store.catalog(Category.PARTICLES).get("minecraft:flame") != LayoutTest.cellColor(0, 3));
    }

    @Test
    void aParticleGroupRepaintsOnlyItsTile() {
        TextureStore store = new TextureStore();
        FakeUpload sheet = new FakeUpload();
        Policy white = LayoutTest.particles(Appearance.solid(0xFFFFFFFF));
        recordSheet(store, LayoutTest.cells(128, 128), sheet, white);
        assertEquals(0, store.repaint(white).repainted);

        Policy fire = LayoutTest.particles(Appearance.solid(0xFFFFFFFF), LayoutTest.group("flame", Appearance.solid(0xFFFF0000)));
        assertEquals(1, store.repaint(fire).repainted);
        int[] pixels = sheet.uploads.get(0);
        assertEquals(0xFFFF0000, pixels[24 * 128 + 4], "flame");
        assertEquals(0xFFFFFFFF, pixels[24 * 128 + 12], "lava");
        assertEquals(0, store.repaint(fire).repainted);

        store.invalidate(LayoutTest.PARTICLES);
        assertEquals(1, store.repaint(fire).repainted);
    }

    @Test
    void colormapsAreChangedInTheGamesOwnArray() {
        TextureStore store = new TextureStore();
        int[] game = {GREEN, GREEN};
        String grass = "minecraft:textures/colormap/grass.png";
        store.record(grass, Layout.of(grass), new PixelBuffer.Heap(2, 1, game), TextureStore.intoArray(game),
                Collections.singletonList(Appearance.ORIGINAL));
        Config c = Config.defaults();
        assertEquals(1, store.repaint(Policy.of(c)).repainted);
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFFFFFFFF}, game, "quake-white removes biome tint");
    }
}
