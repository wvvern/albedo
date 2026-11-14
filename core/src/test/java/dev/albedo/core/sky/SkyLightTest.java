package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyLightTest {
    @Test
    void daylightFollowsTheVanillaSky() {
        assertEquals(1f, SkyLight.brightness(6000, 0, 0), 1e-4f);
        assertEquals(0.04f, SkyLight.brightness(18000, 0, 0), 1e-4f);
        assertEquals(0.7f, SkyLight.brightness(6000, 1, 0), 1e-4f);
        assertEquals(1f, SkyLight.of(SkyPresets.NEBULA, 18000, 0, 0));
    }
}
