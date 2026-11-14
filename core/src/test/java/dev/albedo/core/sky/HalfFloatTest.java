package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HalfFloatTest {
    @Test
    void halfFloatsRoundToTheNearestAndStayInRange() {
        assertEquals(0x3C00, HalfFloat.encode(1f));
        assertEquals(0x3800, HalfFloat.encode(0.5f));
        assertEquals(0x3C01, HalfFloat.encode(1.0009766f));
        assertEquals(0x3555, HalfFloat.encode(1f / 3));
        assertEquals(0x7BFF, HalfFloat.encode(65504f));
        assertEquals(0x7BFF, HalfFloat.encode(1e9f), "the largest half float");
        assertEquals(0, HalfFloat.encode(-1f), "no negative glow");
        assertEquals(0, HalfFloat.encode(Float.NaN), "no bright speck from a NaN");
        assertEquals(1, HalfFloat.encode(5.9604645e-8f), "the smallest subnormal");
        assertEquals(0x0200, HalfFloat.encode(3.0517578e-5f), "a subnormal");
        for (float v = 1e-6f; v < 60000; v *= 1.37f) assertEquals(v, HalfFloat.decode(HalfFloat.encode(v)), v / 1024 + 6e-8f, "" + v);
    }

    @Test
    void aNegativeValueKeepsItsSign() {
        assertEquals((short) 0xBC00, HalfFloat.encodeSigned(-1f));
        assertEquals(0x3C00, HalfFloat.encodeSigned(1f));
        assertEquals(-0.5f, HalfFloat.decode(HalfFloat.encodeSigned(-0.5f)));
    }
}
