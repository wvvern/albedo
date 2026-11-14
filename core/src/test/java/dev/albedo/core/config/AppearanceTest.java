package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static dev.albedo.core.config.ConfigFixtures.recolor;
import static dev.albedo.core.config.ConfigFixtures.solid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppearanceTest {

    @Test
    void aChangeToTheRecolorFieldsRepaints() {
        assertFalse(recolor(0xFF000000, 80, 0, 0).equals(recolor(0xFF000000, 81, 0, 0)));
    }

    @Test
    void editsKeepTheEffects() {
        Appearance a = solid(0xFF808080, 10, 20, 30);
        for (Appearance b : new Appearance[]{a.withMode(Appearance.Mode.RECOLOR), a.withColor(0xFFFFFFFF), a.withAlpha(Appearance.Alpha.OPAQUE),
                a.withRecolor(50, 50, 50)}) {
            assertEquals(Arrays.asList(10, 20, 30), Arrays.asList(b.bevel(), b.outline(), b.chroma().strength()));
        }
        assertEquals(EnumSet.of(Appearance.Field.BEVEL), a.differences(a.withEffects(11, 20, a.chroma())));
        assertEquals(EnumSet.of(Appearance.Field.CHROMA), a.differences(a.withEffects(10, 20, a.chroma().withOptions(90, 50, 50))));
    }
}
