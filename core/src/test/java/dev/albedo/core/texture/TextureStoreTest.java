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
        Config c = Config.defaults();
        CategorySettings cs = new CategorySettings(look, false, java.util.Arrays.asList(groups));
        return Policy.of(c.withProfile(c.activeProfile().with(Category.BLOCKS, cs)));
    }

    private static void record(TextureStore store, String id, FakeUpload upload, Policy policy) {
        recordNamed(store, "minecraft:block/" + id, upload, policy);
    }

    private static void recordNamed(TextureStore store, String name, FakeUpload upload, Policy policy) {
        TextureKey key = TextureClassifier.classify(name);
        store.record(name, key, image(), upload, policy.appearanceFor(key));
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
        TextureStore.Result r = store.repaint(withGroup, null, false);
        assertEquals(1, r.repainted);
        assertEquals(0, stone.uploads.size());
        assertArrayEquals(new int[]{0xFFFF0000, 0x00FF0000}, dirt.uploads.get(0));

        // The same policy again does nothing; turning the mod off gives back the originals.
        assertEquals(0, store.repaint(withGroup, null, false).repainted);
        assertEquals(2, store.repaint(Policy.DISABLED, null, false).repainted);
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
        TextureStore.Result r = store.repaint(blocks(Appearance.solid(0xFFFFFFFF)), null, false);
        assertEquals(0, r.repainted);
        assertEquals(1, r.failed);
        assertFalse(store.catalog(Category.BLOCKS).containsKey("minecraft:stone"), "a gone texture leaves the store");
    }

    @Test
    void aNullUploadKeepsTheTextureThatIsThere() {
        TextureStore store = new TextureStore();
        FakeUpload stone = new FakeUpload();
        record(store, "stone", stone, Policy.DISABLED);
        record(store, "stone", null, Policy.DISABLED);
        assertEquals(1, store.repaint(blocks(Appearance.solid(0xFFFFFFFF)), null, false).repainted);
        assertEquals(1, stone.uploads.size());
        assertTrue(store.complete());
    }

    @Test
    void texturesWithOneKeyAreAllRepainted() {
        // OptiFine connected textures of a block get the key of the block.
        TextureStore store = new TextureStore();
        FakeUpload vanilla = new FakeUpload(), tile = new FakeUpload();
        recordNamed(store, "minecraft:blocks/bookshelf", vanilla, Policy.DISABLED);
        recordNamed(store, "minecraft:optifine/ctm/default/12", tile, Policy.DISABLED);
        assertEquals(TextureClassifier.classify("minecraft:blocks/bookshelf"), TextureClassifier.classify("minecraft:optifine/ctm/default/12"));
        assertEquals(2, store.repaint(blocks(Appearance.solid(0xFFFFFFFF)), null, false).repainted);
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

    @Test
    void colormapsAreChangedInTheGamesOwnArray() {
        TextureStore store = new TextureStore();
        int[] game = {GREEN, GREEN};
        TextureKey key = TextureClassifier.classify("minecraft:textures/colormap/grass.png");
        store.record("minecraft:textures/colormap/grass.png", key, new PixelBuffer.Heap(2, 1, game), TextureStore.intoArray(game),
                Appearance.ORIGINAL);
        Config c = Config.defaults();
        assertEquals(1, store.repaint(Policy.of(c), null, false).repainted);
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFFFFFFFF}, game, "quake-white removes biome tint");
    }
}
