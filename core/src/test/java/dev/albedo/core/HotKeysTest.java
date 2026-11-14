package dev.albedo.core;

import dev.albedo.core.config.Config.KeyBinds;
import dev.albedo.core.config.Config.KeyBinds.Action;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HotKeysTest {
    private final Set<String> down = new HashSet<String>();
    private final HotKeys keys = new HotKeys();

    private Set<Action> poll(KeyBinds binds) {
        return keys.pressed(binds, k -> down.stream().anyMatch(name -> Keys.byName(name) == k));
    }

    @Test
    void aKeyCountsOnceWhenItGoesDown() {
        assertEquals(EnumSet.noneOf(Action.class), poll(KeyBinds.DEFAULT));
        down.add("F6");
        down.add("F9");
        assertEquals(EnumSet.of(Action.TOGGLE, Action.SETTINGS), poll(KeyBinds.DEFAULT));
        assertEquals(EnumSet.noneOf(Action.class), poll(KeyBinds.DEFAULT), "held down");
        down.clear();
        poll(KeyBinds.DEFAULT);
        down.add("F7");
        assertEquals(EnumSet.of(Action.CYCLE_PROFILE), poll(KeyBinds.DEFAULT));
    }

    @Test
    void anUnknownKeyNameIsNeverDown() {
        down.add("F6");
        assertEquals(EnumSet.noneOf(Action.class), poll(new KeyBinds("NONE", "F7", "F9")));
    }
}
