package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static dev.albedo.core.config.ConfigFixtures.solid;
import static dev.albedo.core.config.ConfigFixtures.still;
import static dev.albedo.core.texture.TextureFixtures.GRAY;
import static dev.albedo.core.texture.TextureFixtures.WHITE;
import static dev.albedo.core.texture.TextureFixtures.filled;
import static dev.albedo.core.texture.TextureFixtures.lightness;
import static dev.albedo.core.texture.TextureFixtures.paint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsTest {

    private static PixelBuffer.Heap painted(Appearance a, String location, int w, int h) {
        PixelBuffer.Heap img = filled(w, h, GRAY);
        Layout layout = Layout.of(location);
        paint(layout, Collections.singletonList(a), img);
        return img;
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
            paint(Layout.of(location), Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
            paint(Layout.of(location), Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
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
        paint(Layout.of("minecraft:item/stick"), Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
        paint(Layout.of("minecraft:item/stick"), Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
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
        paint(overlay, Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
        paint(overlay, Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) assertEquals(y == 0, img.getArgb(x, y) != plain.getArgb(x, y), x + "," + y);
        }
    }

    @Test
    void outlineOnAnOpaqueTileIsTheBandAlongTheFrameBorder() {
        PixelBuffer.Heap img = filled(32, 32, GRAY), plain = filled(32, 32, GRAY);
        paint(Layout.of("minecraft:block/stone"), Collections.singletonList(solid(WHITE, 0, 100, 0)), img);
        paint(Layout.of("minecraft:block/stone"), Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
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
            paint(layout, Collections.singletonList(look), img);
            paint(layout, Collections.singletonList(noLine), plain);
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
        paint(layout, Collections.singletonList(edgeChroma(0)), img);
        paint(layout, Collections.singletonList(solid(WHITE, 0, 0, 0)), plain);
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
        paint(layout, Collections.singletonList(outlined), lined);
        paint(layout, Collections.singletonList(outlined.withEffects(0, 0, outlined.chroma())), bare);
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
            assertFalse(degrees > Effects.MUDDY_FROM && degrees < Effects.MUDDY_TO, "hue " + degrees);
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
    void aTextureWithoutTheGpuPassGetsNoChroma() {
        PixelBuffer.Heap img = filled(4, 4, GRAY);
        assertFalse(Effects.apply(solid(GRAY, 0, 0, 100), img, "minecraft:block/stone", false, false, false));
        assertEquals(GRAY, img.getArgb(1, 1), "the colors of a stand-alone texture cannot move");
        // The tiles of a sheet, such as the 1.8.9 particles, have no GPU pass either.
        PixelBuffer.Heap sheet = filled(128, 128, GRAY);
        Layout particles = Layout.of(TextureFixtures.PARTICLES);
        particles.paint(particles.looks(TextureFixtures.policy(Category.PARTICLES, solid(GRAY, 0, 0, 100))), sheet, true);
        assertEquals(GRAY, sheet.getArgb(1, 1));
    }

    @Test
    void effectsKeepAlphaAndSkipVanillaAndHide() {
        PixelBuffer.Heap img = filled(16, 16, 0x80808080);
        paint(Layout.of("minecraft:block/glass"), Collections.singletonList(solid(WHITE, 100, 100, 100)), img);
        assertEquals(0x80, Colors.alpha(img.getArgb(0, 0)));
        for (Appearance.Mode m : new Appearance.Mode[]{Appearance.Mode.ORIGINAL, Appearance.Mode.HIDDEN}) {
            Appearance a = Appearance.of(m).withEffects(100, 100, Chroma.of(100));
            assertFalse(Effects.apply(a, filled(16, 16, GRAY), "minecraft:block/stone", true, true, true), m.name());
        }
        PixelBuffer.Heap tiny = filled(1, 1, GRAY);
        Effects.apply(solid(GRAY, 100, 100, 100), tiny, "minecraft:stone", true, true, true); // no exception on a 1x1 image
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
}
