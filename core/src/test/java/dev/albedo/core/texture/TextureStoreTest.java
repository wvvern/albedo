package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Rule;
import dev.albedo.core.texture.TextureFixtures.FakeUpload;
import dev.albedo.core.texture.TextureFixtures.GpuUpload;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static dev.albedo.core.config.ConfigFixtures.solid;
import static dev.albedo.core.texture.TextureFixtures.GRAY;
import static dev.albedo.core.texture.TextureFixtures.PARTICLES;
import static dev.albedo.core.texture.TextureFixtures.WHITE;
import static dev.albedo.core.texture.TextureFixtures.cellColor;
import static dev.albedo.core.texture.TextureFixtures.cells;
import static dev.albedo.core.texture.TextureFixtures.filled;
import static dev.albedo.core.texture.TextureFixtures.group;
import static dev.albedo.core.texture.TextureFixtures.particles;
import static dev.albedo.core.texture.TextureFixtures.policy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureStoreTest {
    private static final int GREEN = 0xFF2E7D32;

    private static PixelBuffer.Heap image() {
        return new PixelBuffer.Heap(2, 1, new int[]{GREEN, 0x00000000});
    }

    private static Policy blocks(Appearance look, Rule... groups) {
        return policy(Category.BLOCKS, look, groups);
    }

    private static Policy players(Appearance look) {
        return policy(Category.PLAYERS, look);
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
        Policy white = blocks(Appearance.solid(WHITE));
        FakeUpload stone = new FakeUpload(), dirt = new FakeUpload();
        record(store, "stone", stone, white);
        record(store, "dirt", dirt, white);

        // A new group for dirt repaints dirt only, from its original pixels; transparent pixels stay transparent.
        Rule red = new Rule("Ground", Collections.singletonList("dirt"), Appearance.solid(0xFFFF0000), false);
        Policy withGroup = blocks(Appearance.solid(WHITE), red);
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
    void anEffectsOnlyChangeRepaints() {
        TextureStore store = new TextureStore();
        FakeUpload upload = new FakeUpload();
        String name = "minecraft:block/stone";
        Layout layout = Layout.of(name);
        Policy plain = blocks(solid(WHITE, 0, 0, 0));
        store.record(name, layout, filled(16, 16, GRAY), upload, layout.looks(plain));
        assertEquals(0, store.repaint(plain).repainted);
        Policy bevel = blocks(solid(WHITE, 50, 0, 0));
        assertEquals(1, store.repaint(bevel).repainted);
        assertEquals(0, store.repaint(bevel).repainted);
    }

    @Test
    void theStoreIsRepaintingOnlyWhileARepaintRuns() {
        TextureStore store = new TextureStore();
        boolean[] seen = new boolean[2];
        record(store, "stone", pixels -> {
            seen[0] = store.repainting();
            return true;
        }, Policy.DISABLED);
        assertFalse(store.repainting());
        assertEquals(1, store.repaint(blocks(Appearance.solid(WHITE))).repainted);
        assertTrue(seen[0], "an upload runs inside the repaint");
        assertFalse(store.repainting());

        record(store, "dirt", pixels -> {
            seen[1] = store.repainting();
            throw new IllegalStateException("broken");
        }, Policy.DISABLED);
        assertEquals(1, store.repaint(blocks(Appearance.solid(0xFFFF0000))).needReload);
        assertTrue(seen[1]);
        assertFalse(store.repainting(), "a failed upload also ends the repaint");
    }

    @Test
    void goneTexturesAreDroppedAndBrokenOnesReported() {
        TextureStore store = new TextureStore();
        FakeUpload gone = new FakeUpload(), broken = new FakeUpload();
        record(store, "stone", gone, Policy.DISABLED);
        record(store, "dirt", broken, Policy.DISABLED);
        gone.gone = true;
        broken.broken = true;
        TextureStore.Result r = store.repaint(blocks(Appearance.solid(WHITE)));
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
        assertEquals(1, store.repaint(blocks(Appearance.solid(WHITE))).repainted);
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

        TextureStore.Result blocksOnly = store.repaint(blocks(Appearance.solid(WHITE)));
        assertEquals(1, blocksOnly.repainted);
        assertEquals(0, blocksOnly.needReload, "a change to other textures repaints them");

        assertEquals(1, store.repaint(policy(Category.ENTITIES, Appearance.solid(0xFF00FF00))).needReload);
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
        assertEquals(2, store.repaint(blocks(Appearance.solid(WHITE))).repainted);
        assertEquals(1, vanilla.uploads.size());
        assertEquals(1, tile.uploads.size());
        assertEquals(1, store.catalog(Category.BLOCKS).size());
    }

    @Test
    void theVanillaTextureGivesTheCatalogColorWhenItLoadsLast() {
        TextureStore store = new TextureStore();
        String tile = "minecraft:optifine/ctm/default/12", vanilla = "minecraft:blocks/bookshelf";
        store.record(tile, Layout.of(tile), new PixelBuffer.Heap(1, 1, new int[]{0xFFFF0000}), new FakeUpload(), null);
        assertEquals(0xFFFF0000, (int) store.catalog(Category.BLOCKS).get("minecraft:bookshelf"));
        store.record(vanilla, Layout.of(vanilla), new PixelBuffer.Heap(1, 1, new int[]{GREEN}), new FakeUpload(), null);
        assertEquals(GREEN, (int) store.catalog(Category.BLOCKS).get("minecraft:bookshelf"));
        store.forget(vanilla);
        assertEquals(0xFFFF0000, (int) store.catalog(Category.BLOCKS).get("minecraft:bookshelf"));
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
    void downloadedSkinsDoNotCountTowardTheBudget() {
        TextureStore store = new TextureStore(8);
        Policy white = players(Appearance.solid(WHITE));
        record(store, "stone", new FakeUpload(), white);
        for (int i = 0; i < 3; i++) recordNamed(store, "minecraft:skins/" + i, new FakeUpload(), Policy.DISABLED);
        assertTrue(store.complete(), "skins never make the store incomplete");
        assertEquals(3 * 8, store.downloadedBytes());
        assertEquals(1, store.catalog(Category.PLAYERS).size(), "one catalog row for all skins");
        assertTrue(store.catalog(Category.PLAYERS).containsKey("minecraft:skins"));
        assertEquals(3, store.repaint(white).repainted);
    }

    @Test
    void aForgottenSkinFreesItsBytesAndLeavesTheCatalog() {
        TextureStore store = new TextureStore();
        FakeUpload skin = new FakeUpload();
        recordNamed(store, "minecraft:skins/a", skin, Policy.DISABLED);
        assertTrue(store.catalog(Category.PLAYERS).containsKey("minecraft:skins"));
        store.forget("minecraft:skins/a");
        assertEquals(0, store.downloadedBytes());
        assertFalse(store.catalog(Category.PLAYERS).containsKey("minecraft:skins"));
        assertEquals(0, store.repaint(players(Appearance.solid(WHITE))).repainted);
        assertEquals(0, skin.uploads.size());
    }

    @Test
    void aBrokenOrGoneSkinIsForgottenWithoutAReload() {
        TextureStore store = new TextureStore();
        FakeUpload broken = new FakeUpload(), gone = new FakeUpload();
        broken.broken = true;
        gone.gone = true;
        recordNamed(store, "minecraft:skins/a", broken, Policy.DISABLED);
        recordNamed(store, "minecraft:capes/b", gone, Policy.DISABLED);
        TextureStore.Result r = store.repaint(players(Appearance.solid(WHITE)));
        assertEquals(0, r.repainted);
        assertEquals(0, r.needReload);
        assertEquals(0, store.downloadedBytes());
        assertTrue(store.catalog(Category.PLAYERS).isEmpty());
    }

    @Test
    void theCatalogChangesOnlyWhenAKeyComesOrGoes() {
        TextureStore store = new TextureStore();
        recordNamed(store, "minecraft:skins/a", new FakeUpload(), Policy.DISABLED);
        Map<String, Integer> first = store.catalog(Category.PLAYERS);
        recordNamed(store, "minecraft:skins/b", new FakeUpload(), Policy.DISABLED);
        assertSame(first, store.catalog(Category.PLAYERS), "a second skin keeps the catalog");
        recordNamed(store, "minecraft:capes/c", new FakeUpload(), Policy.DISABLED);
        assertTrue(store.catalog(Category.PLAYERS).containsKey("minecraft:capes"));
    }

    @Test
    void skinsKeepTheirTransparentPartsAndCapesDoNot() {
        TextureStore store = new TextureStore();
        FakeUpload skin = new FakeUpload(), steve = new FakeUpload(), cape = new FakeUpload();
        recordNamed(store, "minecraft:skins/a", skin, Policy.DISABLED);
        recordNamed(store, "minecraft:textures/entity/player/wide/steve.png", steve, Policy.DISABLED);
        recordNamed(store, "minecraft:capes/b", cape, Policy.DISABLED);
        Appearance solid = Appearance.solid(0xFFFF0000).withAlpha(Appearance.Alpha.OPAQUE);
        assertEquals(3, store.repaint(players(solid)).repainted);
        assertArrayEquals(new int[]{0xFFFF0000, 0x00FF0000}, skin.uploads.get(0), "the hat layer stays transparent");
        assertArrayEquals(new int[]{0xFFFF0000, 0x00FF0000}, steve.uploads.get(0));
        assertArrayEquals(new int[]{0xFFFF0000, 0xFFFF0000}, cape.uploads.get(0), "a cape takes the shape setting");
    }

    private static void recordSheet(TextureStore store, PixelBuffer.Heap img, TextureStore.Upload upload, Policy policy) {
        Layout layout = Layout.of(PARTICLES);
        store.record(PARTICLES, layout, img, upload, layout.looks(policy));
    }

    @Test
    void theParticleSheetListsItsTilesInTheirOwnCategories() {
        TextureStore store = new TextureStore();
        recordSheet(store, cells(128, 128), new FakeUpload(), Policy.DISABLED);
        assertEquals(cellColor(0, 3), (int) store.catalog(Category.PARTICLES).get("minecraft:flame"));
        assertTrue(store.catalog(Category.PARTICLES).containsKey("minecraft:sga_z"));
        assertFalse(store.catalog(Category.PARTICLES).containsKey("minecraft:particles"), "the sheet shows as its tiles");
        assertFalse(store.catalog(Category.PARTICLES).containsKey("minecraft:fishing_hook"));
        assertTrue(store.catalog(Category.ENTITIES).containsKey("minecraft:fishing_hook"));

        // A resource pack with other pixels gives the texture list new colors.
        PixelBuffer.Heap red = cells(128, 128);
        red.setArgb(0, 24, 0xFFFF0000);
        recordSheet(store, red, new FakeUpload(), Policy.DISABLED);
        assertTrue(store.catalog(Category.PARTICLES).get("minecraft:flame") != cellColor(0, 3));
    }

    @Test
    void aParticleGroupRepaintsOnlyItsTile() {
        TextureStore store = new TextureStore();
        FakeUpload sheet = new FakeUpload();
        Policy white = particles(Appearance.solid(WHITE));
        recordSheet(store, cells(128, 128), sheet, white);
        assertEquals(0, store.repaint(white).repainted);

        Policy fire = particles(Appearance.solid(WHITE), group("flame", Appearance.solid(0xFFFF0000)));
        assertEquals(1, store.repaint(fire).repainted);
        int[] pixels = sheet.uploads.get(0);
        assertEquals(0xFFFF0000, pixels[24 * 128 + 4], "flame");
        assertEquals(WHITE, pixels[24 * 128 + 12], "lava");
        assertEquals(0, store.repaint(fire).repainted);

        store.invalidate(PARTICLES);
        assertEquals(1, store.repaint(fire).repainted);
    }

    @Test
    void movingChromaLeavesItsHueToTheGpuPassAndGoesWhenThePassStops() {
        TextureStore store = new TextureStore();
        GpuUpload upload = new GpuUpload();
        String name = "minecraft:block/stone";
        store.record(name, Layout.of(name), new PixelBuffer.Heap(1, 1, new int[]{WHITE}), upload, null);
        Policy moving = blocks(Appearance.solid(WHITE).withEffects(0, 0, Chroma.of(100)));
        int paints = store.paints();
        assertEquals(1, store.repaint(moving).repainted);
        assertTrue(store.paints() != paints, "a repaint tells the adapter to read the texels again");
        double[] lab = Recolor.lab(upload.uploads.uploads.get(0)[0]);
        assertTrue(Math.hypot(lab[1], lab[2]) < 0.01, "the sprite has no hue of its own");
        int[] texels = new int[1];
        assertTrue(store.chromaTexels(name, 1, 1, texels));
        assertEquals(Chroma.DEFAULT_SPEED, ChromaTexels.speed(texels[0]));

        upload.gpu = false;
        FakeUpload other = new FakeUpload();
        store.record("minecraft:block/dirt", Layout.of("minecraft:block/dirt"), new PixelBuffer.Heap(1, 1, new int[]{WHITE}), other, null);
        store.repaint(moving);
        store.invalidateGpuHue();
        assertEquals(1, store.repaint(moving).repainted, "only the sprite whose GPU hue changed paints again");
        assertEquals(WHITE, upload.uploads.uploads.get(1)[0], "a stopped pass gives no Chroma");
        assertFalse(store.chromaTexels(name, 1, 1, texels));
        assertFalse(store.chromaTexels("minecraft:block/stone_bricks", 1, 1, texels), "an unknown sprite has no texels");
    }

    @Test
    void aTextureOverTheBudgetIsNotKept() {
        TextureStore store = new TextureStore(8);
        String stone = "minecraft:block/stone", dirt = "minecraft:block/dirt";
        assertTrue(store.record(stone, Layout.of(stone), image(), new GpuUpload(), null));
        assertFalse(store.record(dirt, Layout.of(dirt), image(), new GpuUpload(), null), "the GPU pass has no texels for it");
    }

    @Test
    void onlyTexturesWithMovingColorsOnTheGpuTellTheAdapterToReadTheTexels() {
        TextureStore store = new TextureStore();
        Policy policy = blocks(Appearance.solid(WHITE).withEffects(0, 0, Chroma.of(100)));
        int paints = store.paints();
        recordNamed(store, "minecraft:skins/a", new FakeUpload(), policy);
        record(store, "dirt", new GpuUpload(), Policy.DISABLED);
        assertEquals(paints, store.paints(), "a skin and a sprite with still colors change no texels");
        record(store, "stone", new GpuUpload(), policy);
        assertTrue(store.paints() != paints, "a sprite with moving colors changes the texels");
    }

    @Test
    void aChangeOfOnlyTheSpeedChangesTheTexelsButNotThePixels() {
        TextureStore store = new TextureStore();
        GpuUpload upload = new GpuUpload();
        Chroma slow = Chroma.of(100).withOptions(10, Chroma.DEFAULT_SATURATION, Chroma.DEFAULT_BRIGHTNESS);
        Policy before = blocks(Appearance.solid(WHITE).withEffects(0, 0, slow));
        Policy after = blocks(Appearance.solid(WHITE).withEffects(0, 0, slow.withOptions(90, 50, 50)));
        record(store, "stone", upload, before);
        int paints = store.paints();
        assertEquals(0, store.repaint(after).repainted, "the pixels without the hue stay the same");
        assertTrue(store.paints() != paints);
        int[] texels = new int[2];
        assertTrue(store.chromaTexels("minecraft:block/stone", 2, 1, texels));
        assertEquals(90, ChromaTexels.speed(texels[0]));
        upload.gpu = false;
        store.invalidateGpuHue();
        assertEquals(1, store.repaint(before).repainted, "with no GPU pass the pixels get the hue again");
    }

    @Test
    void onlyACategoryWithSpritesThatTheGameDrawsHasMovingColors() {
        TextureStore store = new TextureStore();
        record(store, "stone", new GpuUpload(), Policy.DISABLED);
        assertTrue(store.movingChroma(Category.BLOCKS));
        recordNamed(store, "minecraft:textures/entity/chest/normal.png", new FakeUpload(), Policy.DISABLED);
        assertFalse(store.movingChroma(Category.BLOCK_ENTITIES), "a stand-alone chest keeps still colors");
        recordNamed(store, "minecraft:block/destroy_stage_0", new GpuUpload(), Policy.DISABLED);
        assertTrue(store.movingChroma(Category.BLOCK_BREAKING), "a breaking stage from the block atlas moves");
        recordNamed(store, "minecraft:textures/block/destroy_stage_0.png", new FakeUpload(), Policy.DISABLED);
        assertFalse(store.movingChroma(Category.BLOCK_BREAKING), "the game draws the stand-alone breaking stage");
    }

    @Test
    void colormapsAreChangedInTheGamesOwnArray() {
        TextureStore store = new TextureStore();
        int[] game = {GREEN, GREEN};
        String grass = "minecraft:textures/colormap/grass.png";
        store.record(grass, Layout.of(grass), new PixelBuffer.Heap(2, 1, game), TextureStore.intoArray(game),
                Collections.singletonList(Appearance.ORIGINAL));
        assertEquals(1, store.repaint(Policy.of(Config.defaults())).repainted);
        assertArrayEquals(new int[]{WHITE, WHITE}, game, "quake-white removes biome tint");
    }
}
