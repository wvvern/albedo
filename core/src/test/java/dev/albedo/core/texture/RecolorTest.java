package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static dev.albedo.core.config.ConfigFixtures.recolor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecolorTest {
    /** A 16x16 stone-like texture: grays from dark to light in a fixed pattern. */
    private static PixelBuffer.Heap stone() {
        int[] px = new int[256];
        for (int i = 0; i < px.length; i++) {
            int v = 80 + (i * 37 % 97);
            px[i] = Colors.argb(255, v, v, v);
        }
        return new PixelBuffer.Heap(16, 16, px);
    }

    private static PixelBuffer.Heap apply(Appearance a, PixelBuffer.Heap img) {
        PixelBuffer.Heap copy = img.copy();
        PixelTransformer.apply(a, copy);
        return copy;
    }

    private static double lightness(int argb) {
        return Recolor.lab(argb)[0];
    }

    @Test
    void theTargetIsTheMeanAndTheDetailStays() {
        PixelBuffer.Heap out = apply(recolor(0xFF3A6EA5, 100, 0, 0), stone());
        double[] mean = new double[3];
        for (int i = 0; i < 256; i++) {
            double[] q = Recolor.lab(out.getArgb(i % 16, i / 16));
            for (int k = 0; k < 3; k++) mean[k] += q[k] / 256;
        }
        double[] target = Recolor.lab(0xFF3A6EA5);
        for (int k = 0; k < 3; k++) assertEquals(target[k], mean[k], 0.02, "OKLab channel " + k);
        // Lighter source pixels stay lighter or equal.
        PixelBuffer.Heap src = stone();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                if (lightness(src.getArgb(i % 16, i / 16)) > lightness(src.getArgb(j % 16, j / 16)) + 1e-9) {
                    assertTrue(lightness(out.getArgb(i % 16, i / 16)) >= lightness(out.getArgb(j % 16, j / 16)) - 1e-9);
                }
            }
        }
    }

    @Test
    void theSameColorAndFullDetailGiveTheSameImage() {
        PixelBuffer.Heap src = stone();
        int mean = Recolor.srgb(meanLightness(src), 0, 0);
        PixelBuffer.Heap out = apply(recolor(mean, 100, 100, 0), src);
        for (int i = 0; i < 256; i++) {
            int a = src.getArgb(i % 16, i / 16), b = out.getArgb(i % 16, i / 16);
            assertEquals(Colors.red(a), Colors.red(b), 3);
            assertEquals(Colors.green(a), Colors.green(b), 3);
            assertEquals(Colors.blue(a), Colors.blue(b), 3);
        }
    }

    private static double meanLightness(PixelBuffer img) {
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += lightness(img.getArgb(i % 16, i / 16));
        return sum / 256;
    }

    @Test
    void noDetailIsOneFlatColor() {
        PixelBuffer.Heap out = apply(recolor(0xFFAA3322, 0, 0, 0), stone());
        for (int i = 0; i < 256; i++) assertEquals(0xFFAA3322, out.getArgb(i % 16, i / 16));
    }

    @Test
    void aWhiteOrBlackColorKeepsVisibleDetail() {
        for (int color : new int[]{0xFFFFFFFF, 0xFF000000}) {
            Set<Integer> values = new HashSet<Integer>();
            PixelBuffer.Heap out = apply(recolor(color, 100, 0, 0), stone());
            for (int i = 0; i < 256; i++) values.add(out.getArgb(i % 16, i / 16));
            assertTrue(values.size() > 10, "detail stays for " + Colors.format(color) + ": " + values.size() + " colors");
        }
    }

    @Test
    void lightKeepsTheTextureLightness() {
        PixelBuffer.Heap src = stone();
        PixelBuffer.Heap out = apply(recolor(0xFF202060, 100, 0, 100), src);
        assertEquals(meanLightness(src), meanLightness(out), 0.03);
    }

    @Test
    void holesGetTheColorWithTheSolidShape() {
        PixelBuffer.Heap leaves = stone();
        for (int i = 0; i < 256; i += 3) leaves.setArgb(i % 16, i / 16, 0x00000000);
        Appearance a = recolor(0xFF40A040, 100, 0, 0).withAlpha(Appearance.Alpha.OPAQUE);
        PixelBuffer.Heap out = apply(a, leaves);
        double hole = lightness(out.getArgb(0, 0)), target = lightness(0xFF40A040);
        assertEquals(255, Colors.alpha(out.getArgb(0, 0)));
        assertEquals(target, hole, 0.06, "a hole has the color, not dark noise");
    }

    @Test
    void aTransparentImageIsFilledLikeTheColorStyle() {
        PixelBuffer.Heap empty = new PixelBuffer.Heap(4, 4, new int[16]);
        Appearance a = recolor(0xFF123456, 100, 0, 0).withAlpha(Appearance.Alpha.OPAQUE);
        PixelBuffer.Heap out = apply(a, empty);
        for (int i = 0; i < 16; i++) assertEquals(0xFF123456, out.getArgb(i % 4, i / 4));
    }

    @Test
    void framesOfAStripStayAlike() {
        PixelBuffer.Heap frame = stone();
        int[] strip = new int[16 * 32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 16; x++) strip[y * 16 + x] = frame.getArgb(x, y % 16);
        PixelBuffer.Heap out = apply(recolor(0xFF905020, 80, 20, 0), new PixelBuffer.Heap(16, 32, strip));
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) assertEquals(out.getArgb(x, y), out.getArgb(x, y + 16));
    }

    @Test
    void oreSpecksKeepTheirColor() {
        PixelBuffer.Heap ore = stone();
        ore.setArgb(5, 5, 0xFF2040FF);
        PixelBuffer.Heap out = apply(recolor(0xFFC03030, 100, 0, 0), ore);
        int speck = out.getArgb(5, 5);
        assertTrue(Colors.blue(speck) > Colors.red(speck), "the blue speck stays blue: " + Colors.format(speck));
    }

    @Test
    void aTextureWithManyColorsStaysFast() {
        int[] px = new int[256 * 256];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | (i * 2654435761L % 0xFFFFFF > 0 ? (int) (i * 2654435761L % 0xFFFFFF) : i);
        PixelBuffer.Heap noisy = new PixelBuffer.Heap(256, 256, px);
        long start = System.nanoTime();
        apply(recolor(0xFF3A6EA5, 100, 50, 0), noisy);
        assertTrue((System.nanoTime() - start) / 1000000 < 2000, "a full cache does not slow the recolor down");
    }
}
