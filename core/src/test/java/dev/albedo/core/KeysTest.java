package dev.albedo.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeysTest {

    @Test
    void keysResolveForEveryEra() {
        assertEquals("key.keyboard.f6", Keys.byName("f6").minecraftName);
        assertEquals("key.keyboard.keypad.3", Keys.byName("KP_3").minecraftName);
        assertEquals(0x40, Keys.byName("F6").lwjgl2);
        assertNull(Keys.byName("NONE"));
    }
}
