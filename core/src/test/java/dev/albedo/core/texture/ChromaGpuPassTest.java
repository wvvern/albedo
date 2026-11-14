package dev.albedo.core.texture;

import dev.albedo.core.Colors;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static dev.albedo.core.config.ConfigFixtures.solid;
import static dev.albedo.core.config.ConfigFixtures.still;
import static dev.albedo.core.texture.TextureFixtures.GRAY;
import static dev.albedo.core.texture.TextureFixtures.WHITE;
import static dev.albedo.core.texture.TextureFixtures.filled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the CPU colors and the texels of Effects against an emulation of the GPU pass in chroma.glsl. */
class ChromaGpuPassTest {

    private static Appearance moving(int color) {
        return Appearance.solid(color).withEffects(0, 0, Chroma.of(100));
    }

    /** The color math of the GPU pass in chroma.glsl: the hue of a texel at a turn, added to a painted pixel in OKLab. */
    private static int gpuPass(int painted, int texel, double turn) {
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

    private static double distance(int a, int b) {
        double[] p = Recolor.lab(a), q = Recolor.lab(b);
        return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
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
                    // The CPU fits a color into sRGB before the edge effects change its lightness and the pass only after
                    // them, so Edge reaches hues near the edge of sRGB, where one step of the 8-bit amount moves a channel more.
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
        Set<Integer> colors = new HashSet<Integer>();
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
