package dev.albedo.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {

    @Test
    void everyOpaqueColorSurvivesTheHsvRoundTrip() {
        for (int r = 0; r < 256; r += 5) {
            for (int g = 0; g < 256; g += 3) {
                for (int b = 0; b < 256; b += 7) {
                    int argb = Colors.argb(255, r, g, b);
                    float[] hsv = Colors.toHsv(argb);
                    assertEquals(argb, Colors.fromHsv(hsv[0], hsv[1], hsv[2]), Colors.format(argb));
                }
            }
        }
    }

    @Test
    void hsvGivesThePrimariesAndGrays() {
        assertEquals(0xFFFF0000, Colors.fromHsv(0, 1, 1));
        assertEquals(0xFF00FF00, Colors.fromHsv(1 / 3f, 1, 1));
        assertEquals(0xFF0000FF, Colors.fromHsv(2 / 3f, 1, 1));
        assertEquals(0xFFFF0000, Colors.fromHsv(1, 1, 1), "the hue wraps");
        assertEquals(0xFF808080, Colors.fromHsv(0.4f, 0, 128 / 255f));
        assertArrayEquals(new float[]{0, 0, 0}, Colors.toHsv(0xFF000000));
        assertArrayEquals(new float[]{0, 1, 1}, Colors.toHsv(0x00FF0000), "the alpha has no effect");
    }
}
