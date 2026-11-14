package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyPresetsTest {
    @Test
    void eachPresetHasItsOwnKeyAndADesign() {
        Set<String> keys = new HashSet<String>();
        for (SkyPresets.Preset p : SkyPresets.all()) {
            // The key is part of a texture id and one word of a harness command.
            assertTrue(p.key().matches("[a-z0-9]+"), p.key());
            assertTrue(keys.add(p.key()), "two presets with the key " + p.key());
            assertTrue(p.facing() >= 0 && p.facing() < 360, p.key());
            assertFalse(p.design().layers().isEmpty(), p.key());
            assertSame(p.design(), SkyPresets.byName(" " + p.key().toUpperCase(Locale.ROOT) + " "), "the key finds the preset in any case");
        }
        assertNull(SkyPresets.byName("none"));
    }
}
