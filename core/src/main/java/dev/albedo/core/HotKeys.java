package dev.albedo.core;

import dev.albedo.core.config.Config.KeyBinds;
import dev.albedo.core.config.Config.KeyBinds.Action;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/** The key presses of the actions, polled once each client tick. */
final class HotKeys {
    private final boolean[] wasDown = new boolean[Action.values().length];

    /**
     * The actions whose key went down since the last poll, in the order of {@link Action}.
     *
     * @param isDown whether a key is down now; false for every key while a screen is open
     */
    Set<Action> pressed(KeyBinds binds, Predicate<Keys.Key> isDown) {
        Set<Action> out = EnumSet.noneOf(Action.class);
        for (Action a : Action.values()) {
            Keys.Key key = Keys.byName(binds.get(a));
            boolean down = key != null && isDown.test(key);
            if (down && !wasDown[a.ordinal()]) out.add(a);
            wasDown[a.ordinal()] = down;
        }
        return out;
    }
}
