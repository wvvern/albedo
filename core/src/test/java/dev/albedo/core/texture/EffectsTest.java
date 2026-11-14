package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
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

    /** A solid look with still Chroma, whose hue the CPU paints, so that a test sees the colors without the GPU pass. */
    private static Appearance solid(int color, int bevel, int outline, int chroma) {
        return Appearance.solid(color).withEffects(bevel, outline, still(Chroma.of(chroma)));
    }

    private static Chroma still(Chroma c) {
        return c.withOptions(0, c.saturation(), c.brightness());
    }

    private static Appearance moving(int color) {
        return Appearance.solid(color).withEffects(0, 0, Chroma.of(100));
    }

    private static PixelBuffer.Heap filled(int w, int h, int argb) {
        int[] px = new int[w * h];
        Arrays.fill(px, argb);
        return new PixelBuffer.Heap(w, h, px);
    }

    private static PixelBuffer.Heap painted(Appearance a, String location, int w, int h) {
        PixelBuffer.Heap img = filled(w, h, GRAY);
        Layout layout = Layout.of(location);
        LayoutTest.paint(layout, Collections.singletonList(a), img);
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
    void edgeEffectsLeaveMobsAndConnectedTexturesAlone() {
        for (String location : new String[]{"minecraft:textures/entity/pig/pig.png", "minecraft:optifine/ctm/default/0_glass_white/12"}) {
            PixelBuffer.Heap img = painted(solid(WHITE, 100, 100, 0), location, 16, 16);
            assertEquals(WHITE, img.getArgb(0, 0), location);
        }
    }

    @Test
    void itemsGetOutlineButNoBevel() {
        assertEquals(WHITE, painted(solid(WHITE, 100, 0, 0), "minecraft:item/diamond", 16, 16).getArgb(0, 0));
        PixelBuffer.Heap img = painted(solid(WHITE, 0, 100, 0), "minecraft:item/diamond", 16, 16);
        assertNotEquals(WHITE, img.getArgb(0, 0));
        assertEquals(WHITE, img.getArgb(1, 1));
        for (Category c : Category.values()) {
            assertEquals(c == Category.BLOCKS || c == Category.FLUIDS, c.hasBevel(), c.key());
            assertEquals(c == Category.BLOCKS || c == Category.FLUIDS || c == Category.ITEMS, c.hasOutline(), c.key());
        }
    }

    /** A 16 x 16 frame that is transparent outside an opaque square from 4 to 11, with a faint pixel in its center. */
    private static PixelBuffer.Heap cutout() {
        PixelBuffer.Heap img = filled(16, 16, 0x00000000);
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) img.setArgb(x, y, GRAY);
        }
        img.setArgb(7, 7, 0x40808080);
        return img;
    }

    @Test
    void outlineFollowsTheShapeOfACutout() {
        for (String location : new String[]{"minecraft:block/ladder", "minecraft:item/stick"}) {
            PixelBuffer.Heap img = cutout(), plain = cutout();
            LayoutTest.paint(Layout.of(location), Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
            LayoutTest.paint(Layout.of(location), Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean edge = x == 4 || x == 11 || y == 4 || y == 11;
                    boolean inside = x >= 4 && x < 12 && y >= 4 && y < 12;
                    String where = location + " at " + x + "," + y;
                    assertEquals(inside && edge, img.getArgb(x, y) != plain.getArgb(x, y), where);
                    assertEquals(Colors.alpha(plain.getArgb(x, y)), Colors.alpha(img.getArgb(x, y)), "alpha stays at " + where);
                }
            }
        }
    }

    @Test
    void outlineOfACutoutIsTwoPixelsWideAt32Pixels() {
        PixelBuffer.Heap img = filled(32, 32, 0x00000000);
        for (int y = 8; y < 24; y++) {
            for (int x = 8; x < 24; x++) img.setArgb(x, y, GRAY);
        }
        PixelBuffer.Heap plain = img.copy();
        LayoutTest.paint(Layout.of("minecraft:item/stick"), Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
        LayoutTest.paint(Layout.of("minecraft:item/stick"), Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                boolean band = x >= 8 && x < 24 && y >= 8 && y < 24 && Math.min(Math.min(x - 8, 23 - x), Math.min(y - 8, 23 - y)) < 2;
                assertEquals(band, img.getArgb(x, y) != plain.getArgb(x, y), x + "," + y);
            }
        }
    }

    @Test
    void outlineOfAnOverlayStaysAtTheFrameBorder() {
        PixelBuffer.Heap img = cutout();
        for (int x = 0; x < 16; x++) img.setArgb(x, 0, GRAY);
        PixelBuffer.Heap plain = img.copy();
        Layout overlay = Layout.of("minecraft:block/grass_block_side_overlay");
        LayoutTest.paint(overlay, Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
        LayoutTest.paint(overlay, Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) assertEquals(y == 0, img.getArgb(x, y) != plain.getArgb(x, y), x + "," + y);
        }
    }

    @Test
    void outlineOnAnOpaqueTileIsTheBandAlongTheFrameBorder() {
        PixelBuffer.Heap img = filled(32, 32, GRAY), plain = filled(32, 32, GRAY);
        LayoutTest.paint(Layout.of("minecraft:block/stone"), Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
        LayoutTest.paint(Layout.of("minecraft:block/stone"), Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                boolean band = Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y)) < 2;
                assertEquals(band, img.getArgb(x, y) != plain.getArgb(x, y), x + "," + y);
            }
        }
    }

    @Test
    void theTexelsMarkThePixelsOfThePaintedOutline() {
        // Binary alpha makes the faint center pixel a hole, so the texels must take the shape after the style.
        Appearance look = Appearance.solid(WHITE).withAlpha(Appearance.Alpha.BINARY).withEffects(0, 100, Chroma.of(100));
        Appearance noLine = look.withEffects(0, 0, look.chroma());
        for (String location : new String[]{"minecraft:block/ladder", "minecraft:item/stick"}) {
            Layout layout = Layout.of(location);
            PixelBuffer.Heap img = cutout(), plain = cutout();
            LayoutTest.paint(layout, Collections.singletonList(look), img);
            LayoutTest.paint(layout, Collections.singletonList(noLine), plain);
            int[] texels = new int[16 * 16];
            assertTrue(layout.chromaTexels(Collections.singletonList(look), cutout(), 16, 16, texels));
            double full = ChromaTexels.amount(texels[5 * 16 + 5]);
            int lined = 0;
            for (int i = 0; i < texels.length; i++) {
                if (Colors.alpha(img.argb()[i]) == 0) continue;
                boolean line = img.argb()[i] != plain.argb()[i];
                assertEquals(line, ChromaTexels.amount(texels[i]) < full - 0.01, location + " at " + i % 16 + "," + i / 16);
                if (line) lined++;
            }
            assertEquals(28 + 8, lined, location + ": the border of the square and the eight pixels around the hole");
        }
    }

    private static Appearance edgeChroma(int speed) {
        return Appearance.solid(WHITE).withEffects(0, 0, new Chroma(100, speed, 50, 50, true));
    }

    @Test
    void edgeChromaColorsOnlyTheLineWithAHueThatGoesAround() {
        String stone = "minecraft:block/stone";
        PixelBuffer.Heap img = painted(edgeChroma(0), stone, 16, 16), plain = painted(solid(WHITE, 0, 0, 0), stone, 16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean line = Math.min(Math.min(x, 15 - x), Math.min(y, 15 - y)) == 0;
                assertEquals(line, img.getArgb(x, y) != plain.getArgb(x, y), x + "," + y);
            }
        }
        double[] a = Recolor.lab(img.getArgb(0, 0)), b = Recolor.lab(img.getArgb(15, 15));
        double turn = Math.abs(Math.atan2(a[2], a[1]) - Math.atan2(b[2], b[1]));
        assertTrue(turn > 1, "opposite corners get different hues: " + turn);
    }

    @Test
    void edgeChromaFollowsTheShapeOfEveryFrameAndAlphaStays() {
        // Two frames of an item strip: the square of cutout(), then the same square one pixel lower.
        PixelBuffer.Heap img = filled(16, 32, 0x00000000);
        for (int f = 0; f < 2; f++) {
            for (int y = 4 + f; y < 12 + f; y++) {
                for (int x = 4; x < 12; x++) img.setArgb(x, 16 * f + y, GRAY);
            }
        }
        PixelBuffer.Heap plain = img.copy(), original = img.copy();
        Layout layout = Layout.of("minecraft:item/stick");
        LayoutTest.paint(layout, Collections.singletonList(edgeChroma(0)), img);
        LayoutTest.paint(layout, Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
        boolean[] union = Effects.edgeMask(plain, "minecraft:item/stick", 16);
        for (int i = 0; i < 16 * 32; i++) {
            int at = i % (16 * 16);
            assertEquals(Colors.alpha(plain.argb()[i]), Colors.alpha(img.argb()[i]), "alpha stays at " + i);
            if (Colors.alpha(plain.argb()[i]) != 0) assertEquals(union[at], img.argb()[i] != plain.argb()[i], "pixel " + i);
        }
        int[] texels = new int[16 * 16];
        assertTrue(layout.chromaTexels(Collections.singletonList(edgeChroma(50)), original, 16, 16, texels));
        for (int i = 0; i < texels.length; i++) assertEquals(union[i], ChromaTexels.speed(texels[i]) > 0, "texel " + i);

        // Outline uses the same line, so that the texels with less hue are the pixels the CPU outlined.
        PixelBuffer.Heap lined = original.copy(), bare = original.copy();
        Appearance outlined = Appearance.solid(WHITE).withEffects(0, 100, Chroma.of(100));
        LayoutTest.paint(layout, Collections.singletonList(outlined), lined);
        LayoutTest.paint(layout, Collections.singletonList(outlined.withEffects(0, 0, outlined.chroma())), bare);
        for (int i = 0; i < 16 * 32; i++) {
            if (Colors.alpha(bare.argb()[i]) != 0) assertEquals(union[i % 256], lined.argb()[i] != bare.argb()[i], "outlined " + i);
        }
    }

    @Test
    void edgeChromaColorsAllOfAMobAndATinyFrame() {
        Appearance chroma = solid(WHITE, 0, 0, 100);
        String pig = "minecraft:textures/entity/pig/pig.png";
        assertEquals(painted(chroma, pig, 16, 16).getArgb(8, 8), painted(edgeChroma(0), pig, 16, 16).getArgb(8, 8));
        String stone = "minecraft:block/stone";
        assertEquals(painted(chroma, stone, 2, 2).getArgb(1, 1), painted(edgeChroma(0), stone, 2, 2).getArgb(1, 1));
        int[] texels = new int[4];
        assertTrue(Effects.texels(edgeChroma(50), "minecraft:block/stone", null, 2, 2, texels));
        assertTrue(ChromaTexels.speed(texels[0]) > 0, "a frame with no line gets the hue everywhere");
    }

    @Test
    void edgeChromaIsAChromaOptionInTheConfig() {
        Config c = ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"chroma\": 50, \"chromaEdge\": true } } } }");
        Appearance look = c.profiles().get("x").get(Category.BLOCKS).appearance();
        assertTrue(look.chroma().edge());
        assertTrue(ConfigCodec.encode(c).contains("\"chromaEdge\": true"), ConfigCodec.encode(c));
        assertEquals(look, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("x").get(Category.BLOCKS).appearance());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"entities\": { \"mode\": \"solid\", \"chroma\": 50, \"chromaEdge\": true } } } }"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"chroma\": 50, \"chromaEdge\": 1 } } } }"));
        assertNotEquals(Chroma.of(50), Chroma.of(50).withEdge(true));
        assertTrue(Chroma.of(50).withEdge(true).withStrength(20).withOptions(0, 10, 10).edge(), "the other options keep Edge");
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
    void chromaGivesEveryPartOfABlockOneHueOnEveryVersion() {
        double grass = Effects.hue("minecraft:block/grass_block_top");
        for (String id : new String[]{"minecraft:block/grass_block_side", "minecraft:block/grass_block_side_overlay",
                "minecraft:blocks/grass_top", "minecraft:blocks/grass_side_overlay", "minecraft:textures/block/grass_block_top.png"}) {
            assertEquals(grass, Effects.hue(id), id);
        }
        assertEquals(Effects.hue("minecraft:block/oak_log_top"), Effects.hue("minecraft:blocks/log_oak_top"));
        assertEquals(Effects.hue("minecraft:block/red_wool"), Effects.hue("minecraft:blocks/wool_colored_red"));
        assertEquals(Effects.hue("minecraft:block/stone_bricks"), Effects.hue("minecraft:blocks/stonebrick"));
        assertEquals(Effects.hue("minecraft:block/water_still"), Effects.hue("minecraft:block/water_flow"));
        assertNotEquals(Effects.hue("minecraft:block/grass_block_top"), Effects.hue("minecraft:block/dirt"));
    }

    @Test
    void chromaSkipsTheMuddyHuesAndLiftsBlack() {
        for (int i = 0; i < 2000; i++) {
            double degrees = Math.toDegrees(Effects.hue("minecraft:block/test" + Integer.toString(i, 36) + "x")) % 360;
            assertFalse(degrees > 70 && degrees < 115, "hue " + degrees);
        }
        int black = painted(solid(0xFF000000, 0, 0, 100), "minecraft:block/stone", 4, 4).getArgb(1, 1);
        assertTrue(lightness(black) > 0.27, "black reads as a color: " + lightness(black));
    }

    @Test
    void chromaGoesThroughTheColorsOnBlockAnimationsOnly() {
        PixelBuffer.Heap water = painted(solid(GRAY, 0, 0, 100), "minecraft:block/water_still", 4, 16);
        assertNotEquals(water.getArgb(1, 1), water.getArgb(1, 5), "the frames of a block animation get different hues");
        PixelBuffer.Heap painting = painted(solid(GRAY, 0, 0, 100), "minecraft:textures/painting/wanderer.png", 16, 32);
        assertEquals(painting.getArgb(1, 1), painting.getArgb(1, 20), "a tall painting is one image");
    }

    @Test
    void mobsAndPlayersHaveNoChroma() {
        assertFalse(Category.ENTITIES.hasChroma());
        assertFalse(Category.PLAYERS.hasChroma());
        assertTrue(Category.BLOCKS.hasChroma());
    }

    @Test
    void aTextureWithoutTheGpuPassGetsNoChroma() {
        PixelBuffer.Heap img = filled(4, 4, GRAY);
        assertFalse(Effects.apply(solid(GRAY, 0, 0, 100), img, "minecraft:block/stone", false, false, false));
        assertEquals(GRAY, img.getArgb(1, 1), "the colors of a stand-alone texture cannot move");
        // The tiles of a sheet, such as the 1.8.9 particles, have no GPU pass either.
        PixelBuffer.Heap sheet = filled(128, 128, GRAY);
        Layout particles = Layout.of("minecraft:textures/particle/particles.png");
        particles.paint(particles.looks(LayoutTest.policy(Category.PARTICLES, solid(GRAY, 0, 0, 100))), sheet, true);
        assertEquals(GRAY, sheet.getArgb(1, 1));
    }

    @Test
    void effectsKeepAlphaAndSkipVanillaAndHide() {
        PixelBuffer.Heap img = filled(16, 16, 0x80808080);
        LayoutTest.paint(Layout.of("minecraft:block/glass"), Collections.singletonList(solid(WHITE, 100, 100, 100)), img);
        assertEquals(0x80, Colors.alpha(img.getArgb(0, 0)));
        for (Appearance.Mode m : new Appearance.Mode[]{Appearance.Mode.ORIGINAL, Appearance.Mode.HIDDEN}) {
            Appearance a = Appearance.of(m).withEffects(100, 100, Chroma.of(100));
            assertFalse(Effects.apply(a, filled(16, 16, GRAY), "minecraft:block/stone", true, true, true), m.name());
        }
        PixelBuffer.Heap tiny = filled(1, 1, GRAY);
        Effects.apply(solid(GRAY, 100, 100, 100), tiny, "minecraft:stone", true, true, true); // no exception on a 1x1 image
    }

    @Test
    void editsKeepTheEffects() {
        Appearance a = solid(GRAY, 10, 20, 30);
        for (Appearance b : new Appearance[]{a.withMode(Appearance.Mode.RECOLOR), a.withColor(WHITE), a.withAlpha(Appearance.Alpha.OPAQUE),
                a.withRecolor(50, 50, 50)}) {
            assertEquals(Arrays.asList(10, 20, 30), Arrays.asList(b.bevel(), b.outline(), b.chroma().strength()));
        }
        assertEquals(EnumSet.of(Appearance.Field.BEVEL), a.differences(a.withEffects(11, 20, a.chroma())));
        assertEquals(EnumSet.of(Appearance.Field.CHROMA), a.differences(a.withEffects(10, 20, a.chroma().withOptions(90, 50, 50))));
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

        Config unused = withBlocks(new CategorySettings(Appearance.ORIGINAL.withEffects(40, 0, Chroma.OFF), false, Collections.<Rule>emptyList()));
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
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"entities\": { \"mode\": \"solid\", \"outline\": 50 } } } }"));
        Config items = ConfigCodec.decode("{ \"profiles\": { \"x\": { \"items\": { \"mode\": \"solid\", \"outline\": 50 } } } }");
        assertEquals(50, items.profiles().get("x").get(Category.ITEMS).appearance().outline());
    }

    @Test
    void anEffectsOnlyChangeRepaints() {
        TextureStore store = new TextureStore();
        TextureStoreTest.FakeUpload upload = new TextureStoreTest.FakeUpload();
        String name = "minecraft:block/stone";
        Layout layout = Layout.of(name);
        Policy plain = LayoutTest.policy(Category.BLOCKS, solid(WHITE, 0, 0, 0));
        TextureStoreTest.record(store, name, layout, filled(16, 16, GRAY), upload, layout.looks(plain));
        assertEquals(0, TextureStoreTest.repaint(store, plain).repainted);
        Policy bevel = LayoutTest.policy(Category.BLOCKS, solid(WHITE, 50, 0, 0));
        assertEquals(1, TextureStoreTest.repaint(store, bevel).repainted);
        assertEquals(0, TextureStoreTest.repaint(store, bevel).repainted);
    }

    @Test
    void defaultSaturationAndBrightnessGiveTheStillChromaColors() {
        // The colors of OKLab chroma 0.13 and the lightness limits 0.3 and 0.78, which the default options keep.
        int[] expected = {0xFF6BD093, 0xFF00324A, 0xFFFA93B4};
        String[] ids = {"minecraft:block/stone", "minecraft:block/dirt", "minecraft:block/oak_planks"};
        for (int i = 0; i < ids.length; i++) {
            PixelBuffer.Heap img = filled(2, 2, i == 1 ? 0xFF000000 : WHITE);
            Effects.apply(solid(i == 1 ? 0xFF000000 : WHITE, 0, 0, 100), img, ids[i], false, false, true);
            assertEquals(Integer.toHexString(expected[i]), Integer.toHexString(img.getArgb(0, 0)), ids[i]);
        }
    }

    @Test
    void saturationAndBrightnessChangeTheColors() {
        Chroma base = Chroma.of(100);
        double[] gray = Recolor.lab(chromaPixel(base.withOptions(30, 0, 50), GRAY)), normal = Recolor.lab(chromaPixel(base, GRAY));
        assertTrue(Math.hypot(gray[1], gray[2]) < 0.01, "saturation 0 gives no color");
        assertTrue(Math.hypot(normal[1], normal[2]) > 0.1, "saturation 50 gives a color");
        double light = lightness(chromaPixel(base.withOptions(30, 50, 100), WHITE));
        double dark = lightness(chromaPixel(base.withOptions(30, 50, 0), WHITE));
        assertTrue(light > lightness(chromaPixel(base, WHITE)) + 0.1 && dark < lightness(chromaPixel(base, WHITE)) - 0.1,
                "brightness moves the lightness: " + dark + " " + light);
    }

    private static int chromaPixel(Chroma c, int color) {
        PixelBuffer.Heap img = filled(1, 1, color);
        Effects.apply(Appearance.solid(color).withEffects(0, 0, still(c)), img, "minecraft:block/stone", false, false, true);
        return img.getArgb(0, 0);
    }

    /** The color math of the GPU pass in chroma.glsl: the hue of a texel at a turn, added to a painted pixel in OKLab. */
    static int gpuPass(int painted, int texel, double turn) {
        if (Colors.alpha(painted) == 0 || ChromaTexels.speed(texel) == 0) return painted;
        double[] lab = Recolor.lab(painted);
        double angle = 2 * Math.PI * (ChromaTexels.hue(texel) + turn), amount = ChromaTexels.amount(texel);
        int rgb = Recolor.srgb(lab[0], lab[1] + amount * Math.cos(angle), lab[2] + amount * Math.sin(angle));
        return Colors.withAlpha(rgb, Colors.alpha(painted));
    }

    private static int maxChannelDifference(int a, int b) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) out = Math.max(out, Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)));
        return out;
    }

    @Test
    void theGpuPassAtTurnZeroGivesTheStillChromaColors() {
        String id = "minecraft:block/stone";
        int[] colors = {WHITE, GRAY, 0xFF000000, 0xFF2060C0, 0xFFE0A030, 0x80C04040};
        // Bevel, Outline and Edge.
        for (int[] edges : new int[][]{{0, 0, 0}, {30, 0, 0}, {0, 60, 0}, {30, 60, 0}, {0, 0, 1}, {30, 60, 1}}) {
            Appearance a = Appearance.solid(WHITE).withEffects(edges[0], edges[1], new Chroma(80, 40, 70, 60, edges[2] == 1));
            double sum = 0;
            int count = 0;
            for (int color : colors) {
                PixelBuffer.Heap still = filled(16, 16, color), neutral = filled(16, 16, color);
                Effects.apply(a.withEffects(a.bevel(), a.outline(), still(a.chroma())), still, id, true, true, true);
                Effects.apply(a, neutral, id, true, true, true);
                int[] texels = new int[16 * 16];
                assertTrue(Effects.texels(a, id, Effects.edgeMask(filled(16, 16, color), id, 16), 16, 16, texels));
                for (int i = 0; i < texels.length; i++) {
                    int cpu = still.argb()[i], gpu = gpuPass(neutral.argb()[i], texels[i], 0);
                    String where = Integer.toHexString(color) + " with " + Arrays.toString(edges) + " at " + i + ": "
                            + Integer.toHexString(cpu) + " vs " + Integer.toHexString(gpu);
                    // The CPU fits a color into sRGB before the edge effects change its lightness, and the pass only after them.
                    // Edge reaches hues near the edge of sRGB, where one step of the 8-bit amount moves a channel more.
                    if (edges[0] == 0 && edges[1] == 0 && edges[2] == 0) assertTrue(maxChannelDifference(cpu, gpu) <= 3, where);
                    double d = distance(cpu, gpu);
                    assertTrue(d < 0.06, where);
                    sum += d;
                    count++;
                }
            }
            assertTrue(sum / count < 0.012, "mean OKLab distance with " + Arrays.toString(edges) + ": " + sum / count);
        }
    }

    private static double distance(int a, int b) {
        double[] p = Recolor.lab(a), q = Recolor.lab(b);
        return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
    }

    @Test
    void theGpuHueGoesOnlyToMovingChroma() {
        String id = "minecraft:block/stone";
        Appearance still = solid(GRAY, 0, 0, 100);
        PixelBuffer.Heap b = filled(4, 4, GRAY);
        Effects.apply(still, b, id, false, false, true);
        double[] stillLab = Recolor.lab(b.getArgb(1, 1));
        assertTrue(Math.hypot(stillLab[1], stillLab[2]) > 0.1, "at Speed 0 the CPU paints the hue");
        assertFalse(Effects.texels(still, id, null, 4, 4, new int[16]), "still colors have no GPU texels");
        Appearance moving = moving(GRAY);
        PixelBuffer.Heap neutral = filled(4, 4, WHITE);
        Effects.apply(moving, neutral, id, false, false, true);
        double[] lab = Recolor.lab(neutral.getArgb(1, 1));
        assertTrue(Math.hypot(lab[1], lab[2]) < 0.01, "the CPU leaves the hue to the pass");
    }

    @Test
    void theOutlineBorderGetsLessHue() {
        int[] texels = new int[16 * 16];
        Appearance a = Appearance.solid(WHITE).withEffects(0, 100, Chroma.of(100));
        assertTrue(Effects.texels(a, "minecraft:block/stone", Effects.edgeMask(filled(16, 16, WHITE), "minecraft:block/stone", 16), 16, 16, texels));
        assertEquals(ChromaTexels.amount(texels[8 * 16 + 8]) / 2, ChromaTexels.amount(texels[0]), 0.002);
        assertEquals(ChromaTexels.amount(texels[8 * 16 + 8]), ChromaTexels.amount(texels[16 + 1]), 1e-9, "the second ring has the full hue");
        assertEquals(Effects.hue("minecraft:block/stone") / (2 * Math.PI), ChromaTexels.hue(texels[0]), 1e-4);
        assertEquals(Chroma.DEFAULT_SPEED, ChromaTexels.speed(texels[0]));
    }

    @Test
    void theGpuPassTurnsTheHueSmoothly() {
        Appearance a = moving(GRAY);
        String id = "minecraft:block/stone";
        PixelBuffer.Heap neutral = filled(1, 1, GRAY);
        Effects.apply(a, neutral, id, false, false, true);
        int[] texel = new int[1];
        Effects.texels(a, id, null, 1, 1, texel);
        java.util.Set<Integer> colors = new java.util.HashSet<Integer>();
        double before = Double.NaN;
        for (int frame = 0; frame < 120; frame++) {
            int c = gpuPass(neutral.getArgb(0, 0), texel[0], frame / 120.0);
            colors.add(c);
            double[] lab = Recolor.lab(c);
            double hue = Math.toDegrees(Math.atan2(lab[2], lab[1]));
            if (!Double.isNaN(before)) {
                double step = ((hue - before) % 360 + 540) % 360 - 180;
                assertEquals(3, step, 2.5, "frame " + frame + " moves the hue by a small step");
            }
            before = hue;
        }
        assertTrue(colors.size() > 100, "more than 48 colors in one turn: " + colors.size());
    }
}
