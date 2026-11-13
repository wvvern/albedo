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

    private static Appearance solid(int color, int bevel, int outline, int chroma) {
        return Appearance.solid(color).withEffects(bevel, outline, Chroma.of(chroma));
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
    void chromaColorsStayStillOnMobsAndPlayers() {
        Appearance moving = solid(GRAY, 0, 0, 100);
        TextureKey trim = TextureClassifier.classify("minecraft:trims/models/armor/coast");
        assertEquals(Category.ENTITIES, trim.category());
        assertFalse(LayoutTest.policy(Category.ENTITIES, moving).appearanceFor(trim).chromaMoves(), "an armor trim sprite stays still with its mob");
        TextureKey skin = TextureClassifier.classify("minecraft:skins/abc");
        assertFalse(LayoutTest.policy(Category.PLAYERS, moving).appearanceFor(skin).chromaMoves());
        TextureKey stone = TextureClassifier.classify("minecraft:block/stone");
        assertTrue(LayoutTest.policy(Category.BLOCKS, moving).appearanceFor(stone).chromaMoves());
    }

    @Test
    void effectsKeepAlphaAndSkipVanillaAndHide() {
        PixelBuffer.Heap img = filled(16, 16, 0x80808080);
        LayoutTest.paint(Layout.of("minecraft:block/glass"), Collections.singletonList(solid(WHITE, 100, 100, 100)), img);
        assertEquals(0x80, Colors.alpha(img.getArgb(0, 0)));
        assertFalse(Appearance.ORIGINAL.withEffects(100, 100, Chroma.of(100)).hasEffects());
        assertFalse(Appearance.of(Appearance.Mode.HIDDEN).withEffects(100, 100, Chroma.of(100)).hasEffects());
        PixelBuffer.Heap tiny = filled(1, 1, GRAY);
        Effects.apply(solid(GRAY, 100, 100, 100), tiny, "minecraft:stone", true, false); // no exception on a 1x1 image
    }

    @Test
    void editsKeepTheEffects() {
        Appearance a = solid(GRAY, 10, 20, 30);
        for (Appearance b : new Appearance[]{a.withMode(Appearance.Mode.RECOLOR), a.withColor(WHITE), a.withAlpha(Appearance.Alpha.OPAQUE),
                a.withRecolor(50, 50, 50)}) {
            assertEquals(Arrays.asList(10, 20, 30), Arrays.asList(b.bevel(), b.outline(), b.chroma().strength()));
        }
        assertEquals(EnumSet.of(Appearance.Field.BEVEL), a.differences(a.withEffects(11, 20, Chroma.of(30))));
        assertEquals(EnumSet.of(Appearance.Field.CHROMA), a.differences(a.withEffects(10, 20, Chroma.of(30).withOptions(90, 50, 50))));
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
            Effects.apply(solid(i == 1 ? 0xFF000000 : WHITE, 0, 0, 100), img, ids[i], false, false);
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
        Effects.apply(Appearance.solid(color).withEffects(0, 0, c), img, "minecraft:block/stone", false, false);
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
        for (int[] edges : new int[][]{{0, 0}, {30, 0}, {0, 60}, {30, 60}}) {
            Appearance a = Appearance.solid(WHITE).withEffects(edges[0], edges[1], new Chroma(80, 40, 70, 60));
            double sum = 0;
            int count = 0;
            for (int color : colors) {
                PixelBuffer.Heap still = filled(16, 16, color), neutral = filled(16, 16, color);
                Effects.apply(a, still, id, true, false);
                Effects.apply(a, neutral, id, true, true);
                int[] texels = new int[16 * 16];
                assertTrue(Effects.texels(a, id, true, 16, 16, texels));
                for (int i = 0; i < texels.length; i++) {
                    int cpu = still.argb()[i], gpu = gpuPass(neutral.argb()[i], texels[i], 0);
                    String where = Integer.toHexString(color) + " with " + Arrays.toString(edges) + " at " + i + ": "
                            + Integer.toHexString(cpu) + " vs " + Integer.toHexString(gpu);
                    // The CPU fits a color into sRGB before the edge effects change its lightness, and the pass only after them.
                    if (edges[0] == 0 && edges[1] == 0) assertTrue(maxChannelDifference(cpu, gpu) <= 3, where);
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
        Appearance still = solid(GRAY, 0, 0, 100).withEffects(0, 0, Chroma.of(100).withOptions(0, 50, 50));
        PixelBuffer.Heap a = filled(4, 4, GRAY), b = filled(4, 4, GRAY);
        Effects.apply(still, a, id, false, false);
        Effects.apply(still, b, id, false, true);
        assertEquals(a.getArgb(1, 1), b.getArgb(1, 1), "still colors stay on the CPU");
        assertFalse(Effects.texels(still, id, false, 4, 4, new int[16]), "still colors have no GPU texels");
        Appearance moving = solid(GRAY, 0, 0, 100);
        PixelBuffer.Heap neutral = filled(4, 4, WHITE);
        Effects.apply(moving, neutral, id, false, true);
        double[] lab = Recolor.lab(neutral.getArgb(1, 1));
        assertTrue(Math.hypot(lab[1], lab[2]) < 0.01, "the CPU leaves the hue to the pass");
    }

    @Test
    void theOutlineBorderGetsLessHue() {
        int[] texels = new int[16 * 16];
        assertTrue(Effects.texels(Appearance.solid(WHITE).withEffects(0, 100, Chroma.of(100)), "minecraft:block/stone", true, 16, 16, texels));
        assertEquals(ChromaTexels.amount(texels[8 * 16 + 8]) / 2, ChromaTexels.amount(texels[0]), 0.002);
        assertEquals(ChromaTexels.amount(texels[8 * 16 + 8]), ChromaTexels.amount(texels[16 + 1]), 1e-9, "the second ring has the full hue");
        assertEquals(Effects.hue("minecraft:block/stone") / (2 * Math.PI), ChromaTexels.hue(texels[0]), 1e-4);
        assertEquals(Chroma.DEFAULT_SPEED, ChromaTexels.speed(texels[0]));
    }

    @Test
    void theGpuPassTurnsTheHueSmoothly() {
        Appearance a = solid(GRAY, 0, 0, 100);
        String id = "minecraft:block/stone";
        PixelBuffer.Heap neutral = filled(1, 1, GRAY);
        Effects.apply(a, neutral, id, false, true);
        int[] texel = new int[1];
        Effects.texels(a, id, false, 1, 1, texel);
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
