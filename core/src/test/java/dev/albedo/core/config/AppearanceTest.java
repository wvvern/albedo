package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

import static dev.albedo.core.config.ConfigFixtures.recolor;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppearanceTest {

    @Test
    void aChangeToTheRecolorFieldsRepaints() {
        assertFalse(recolor(0xFF000000, 80, 0, 0).equals(recolor(0xFF000000, 81, 0, 0)));
    }
}
