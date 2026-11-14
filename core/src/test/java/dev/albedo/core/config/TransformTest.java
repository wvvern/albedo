package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformTest {

    @Test
    void yawWrapsAndTheRotationPointsTheCenter() {
        assertEquals(new Transform(0, 0, 0, 100), new Transform(360, 0, 0, 100));
        double[] r = new Transform(90, 30, 0, 100).rotation();
        // The local north (0, 0, -1) goes to the third column, negated.
        double x = -r[2], y = -r[5], z = -r[8];
        assertEquals(Math.cos(Math.toRadians(30)), x, 1e-9, "east");
        assertEquals(Math.sin(Math.toRadians(30)), y, 1e-9, "up");
        assertEquals(0, z, 1e-9);
        assertArrayEquals(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, Transform.IDENTITY.rotation(), 1e-12);
    }
}
