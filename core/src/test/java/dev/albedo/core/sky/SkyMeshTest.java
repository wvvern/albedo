package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyMeshTest {
    @Test
    void theQuadCoversTheImageWithTheDirectionsOfTheViewCorners() {
        float[] whole = SkyMesh.quad(null);
        assertEquals(6 * 5, whole.length);
        assertArrayEquals(new float[] {0, 0, 0, 1, 1}, Arrays.copyOfRange(whole, 10, 15), "the third vertex is the bottom right");
        float[] view = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        float[] quad = SkyMesh.quad(view);
        assertArrayEquals(new float[] {7, 8, 9, 1, 1}, Arrays.copyOfRange(quad, 10, 15), "the bottom right takes the third corner");
        assertArrayEquals(new float[] {10, 11, 12, 1, 0}, Arrays.copyOfRange(quad, 25, 30), "the top right takes the fourth corner");
    }
}
