package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static dev.albedo.core.sky.SkyFixtures.fromHalf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HalfFloatTest {
    @Test
    void halfFloatsRoundToTheNearestAndStayInRange() {
        assertEquals(0x3C00, SkyGenerator.half(1f));
        assertEquals(0x3800, SkyGenerator.half(0.5f));
        assertEquals(0x3C01, SkyGenerator.half(1.0009766f));
        assertEquals(0x3555, SkyGenerator.half(1f / 3));
        assertEquals(0x7BFF, SkyGenerator.half(65504f));
        assertEquals(0x7BFF, SkyGenerator.half(1e9f), "the largest half float");
        assertEquals(0, SkyGenerator.half(-1f), "no negative glow");
        assertEquals(0, SkyGenerator.half(Float.NaN), "no bright speck from a NaN");
        assertEquals(1, SkyGenerator.half(5.9604645e-8f), "the smallest subnormal");
        assertEquals(0x0200, SkyGenerator.half(3.0517578e-5f), "a subnormal");
        for (float v = 1e-6f; v < 60000; v *= 1.37f) assertEquals(v, fromHalf(SkyGenerator.half(v)), v / 1024 + 6e-8f, "" + v);
    }
}
