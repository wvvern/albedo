package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsTest {
    private static final int GRAY = 0xFF808080, WHITE = 0xFFFFFFFF;

    private static Appearance solid(int color, int bevel, int outline, int chroma) {
        return Appearance.solid(color).withEffects(bevel, outline, chroma);
    }

    private static PixelBuffer.Heap filled(int w, int h, int argb) {
        int[] px = new int[w * h];
        Arrays.fill(px, argb);
        return new PixelBuffer.Heap(w, h, px);
    }

    private static PixelBuffer.Heap painted(Appearance a, String location, int w, int h) {
        PixelBuffer.Heap img = filled(w, h, GRAY);
        Layout layout = Layout.of(location);
        layout.paint(Collections.singletonList(a), img, null);
        return img;
    }

    private static double lightness(int argb) {
        return Recolor.lab(argb)[0];
    }

    @Test
    void bevelShadesTheEdgesOfABlockTheSameOnEverySide() {
        PixelBuffer.Heap img = painted(solid(WHITE, 100, 0, 0), "minecraft:block/stone", 16, 16);
        double edge = lightness(img.getArgb(0, 8)), center = lightness(img.getArgb(8, 8));
        assertTrue(edge < center - 0.1, "a white block gets darker edges: " + edge + " vs " + center);
        for (int[] p : new int[][]{{15, 8}, {8, 0}, {8, 15}}) assertEquals(img.getArgb(0, 8), img.getArgb(p[0], p[1]), "every side is the same");
        assertTrue(lightness(painted(solid(0xFF101010, 100, 0, 0), "minecraft:block/stone", 16, 16).getArgb(0, 8)) > lightness(0xFF101010),
                "a dark block gets lighter edges");
    }

    @Test
    void outlineChangesOnlyTheBorder() {
        PixelBuffer.Heap img = painted(solid(WHITE, 0, 100, 0), "minecraft:block/stone", 16, 16);
        assertNotEquals(WHITE, img.getArgb(0, 0));
        assertEquals(WHITE, img.getArgb(1, 1), "the second ring stays the color");
        assertEquals(WHITE, img.getArgb(8, 8));
    }

    @Test
    void edgeEffectsGoOnEachFrameOfABlockAnimation() {
        PixelBuffer.Heap strip = painted(solid(WHITE, 100, 100, 0), "minecraft:block/water_still", 16, 64);
        PixelBuffer.Heap single = painted(solid(WHITE, 100, 100, 0), "minecraft:block/stone", 16, 16);
        for (int f = 0; f < 4; f++) {
            for (int y = 0; y < 16; y++) assertEquals(single.getArgb(3, y), strip.getArgb(3, f * 16 + y), "frame " + f + " row " + y);
        }
    }

    @Test
    void edgeEffectsLeaveItemsMobsAndConnectedTexturesAlone() {
        for (String location : new String[]{"minecraft:item/diamond", "minecraft:textures/entity/pig/pig.png",
                "minecraft:optifine/ctm/default/0_glass_white/12"}) {
            PixelBuffer.Heap img = painted(solid(WHITE, 100, 100, 0), location, 16, 16);
            assertEquals(WHITE, img.getArgb(0, 0), location);
        }
    }

    @Test
    void chromaGivesEachKindOfTextureItsOwnStableHue() {
        int stone = painted(solid(WHITE, 0, 0, 100), "minecraft:block/stone", 4, 4).getArgb(1, 1);
        int dirt = painted(solid(WHITE, 0, 0, 100), "minecraft:block/dirt", 4, 4).getArgb(1, 1);
        assertNotEquals(WHITE, stone, "white gets a color");
        assertNotEquals(stone, dirt);
        assertEquals(stone, painted(solid(WHITE, 0, 0, 100), "minecraft:block/stone", 4, 4).getArgb(1, 1));
        assertEquals(Effects.hue("minecraft:generic_0"), Effects.hue("minecraft:generic_7"), "frames of one particle share a hue");
    }

    @Test
    void chromaGoesThroughTheColorsOnBlockAnimationsOnly() {
        PixelBuffer.Heap water = painted(solid(GRAY, 0, 0, 100), "minecraft:block/water_still", 4, 16);
        assertNotEquals(water.getArgb(1, 1), water.getArgb(1, 5), "the frames of a block animation get different hues");
        PixelBuffer.Heap painting = painted(solid(GRAY, 0, 0, 100), "minecraft:textures/painting/wanderer.png", 16, 32);
        assertEquals(painting.getArgb(1, 1), painting.getArgb(1, 20), "a tall painting is one image");
    }

    @Test
    void effectsKeepAlphaAndSkipVanillaAndHide() {
        PixelBuffer.Heap img = filled(16, 16, 0x80808080);
        Layout.of("minecraft:block/glass").paint(Collections.singletonList(solid(WHITE, 100, 100, 100)), img, null);
        assertEquals(0x80, Colors.alpha(img.getArgb(0, 0)));
        assertFalse(Appearance.ORIGINAL.withEffects(100, 100, 100).hasEffects());
        assertFalse(Appearance.of(Appearance.Mode.HIDDEN).withEffects(100, 100, 100).hasEffects());
        PixelBuffer.Heap tiny = filled(1, 1, GRAY);
        Effects.apply(solid(GRAY, 100, 100, 100), tiny, "minecraft:stone", true); // no exception on a 1x1 image
    }

    @Test
    void editsKeepTheEffects() {
        Appearance a = solid(GRAY, 10, 20, 30);
        for (Appearance b : new Appearance[]{a.withMode(Appearance.Mode.RECOLOR), a.withColor(WHITE), a.withAlpha(Appearance.Alpha.OPAQUE),
                a.withTexture("x.png"), a.withRecolor(50, 50, 50)}) {
            assertEquals(Arrays.asList(10, 20, 30), Arrays.asList(b.bevel(), b.outline(), b.chroma()));
        }
        assertEquals(EnumSet.of(Appearance.Field.BEVEL), a.differences(a.withEffects(11, 20, 30)));
    }

    private static Config withBlocks(CategorySettings blocks) {
        Config c = Config.defaults();
        Profile p = c.activeProfile().renamed("mine").with(Category.BLOCKS, blocks);
        return c.withProfile(p).withActiveProfile("mine");
    }

    @Test
    void effectsGoIntoTheFileOnlyWhereALookUsesThem() {
        Config used = withBlocks(new CategorySettings(solid(WHITE, 40, 0, 70), false, Collections.<Rule>emptyList()));
        String json = ConfigCodec.encode(used);
        assertTrue(json.contains("\"bevel\": 40") && json.contains("\"chroma\": 70"), json);
        assertEquals(used, ConfigCodec.decode(json));

        Config unused = withBlocks(new CategorySettings(Appearance.ORIGINAL.withEffects(40, 0, 0), false, Collections.<Rule>emptyList()));
        assertFalse(ConfigCodec.encode(unused).contains("bevel"), "Vanilla keeps the file readable for 0.2.0");
    }

    @Test
    void aGroupCanTurnOffABevelThatItsCategoryHas() {
        CategorySettings cs = new CategorySettings(solid(WHITE, 40, 0, 0), false, Collections.<Rule>emptyList());
        Rule glass = new Rule("Glass", Collections.singletonList("glass*"), solid(WHITE, 40, 0, 0), false)
                .withLook(solid(WHITE, 0, 0, 0), cs.appearance());
        Config c = withBlocks(cs.withRuleAt(0, glass));
        String json = ConfigCodec.encode(c);
        assertTrue(json.contains("\"bevel\": 0"), json);
        Policy policy = Policy.of(ConfigCodec.decode(json));
        assertEquals(0, policy.appearanceFor(new TextureKey(Category.BLOCKS, "minecraft:glass")).bevel());
        assertEquals(40, policy.appearanceFor(new TextureKey(Category.BLOCKS, "minecraft:stone")).bevel());
    }

    @Test
    void effectKeysWhereTheyHaveNoMeaningAreErrors() {
        IllegalArgumentException sky = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"sky\": { \"mode\": \"solid\", \"chroma\": 50 } } } }"));
        assertTrue(sky.getMessage().contains("x.sky"), sky.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"items\": { \"mode\": \"solid\", \"bevel\": 50 } } } }"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"outline\": 101 } } } }"));
    }

    @Test
    void anEffectsOnlyChangeRepaints() {
        TextureStore store = new TextureStore();
        TextureStoreTest.FakeUpload upload = new TextureStoreTest.FakeUpload();
        String name = "minecraft:block/stone";
        Layout layout = Layout.of(name);
        Policy plain = LayoutTest.policy(Category.BLOCKS, solid(WHITE, 0, 0, 0));
        store.record(name, layout, filled(16, 16, GRAY), upload, layout.looks(plain));
        assertEquals(0, store.repaint(plain, null, false).repainted);
        Policy bevel = LayoutTest.policy(Category.BLOCKS, solid(WHITE, 50, 0, 0));
        assertEquals(1, store.repaint(bevel, null, false).repainted);
        assertEquals(0, store.repaint(bevel, null, false).repainted);
    }
}
