package dev.albedo.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Key names usable in the config, polled as raw keys because keybinding APIs differ on every loader and version. */
public final class Keys {
    public static final class Key {
        /**
         * The stable translation key ("key.keyboard.f6"), which works through InputConstants on 1.13+ although
         * 1.13-1.21.x use GLFW codes and 26.x uses SDL scancodes.
         */
        public final String minecraftName;
        /** The LWJGL 2 code that 1.8.9-1.12.2 use. */
        public final int lwjgl2;

        Key(String minecraftName, int lwjgl2) {
            this.minecraftName = minecraftName;
            this.lwjgl2 = lwjgl2;
        }
    }

    private static final Map<String, Key> KEYS = new HashMap<String, Key>();

    static {
        int[] lwjglF = {0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x57, 0x58};
        for (int i = 0; i < 12; i++) put("F" + (i + 1), "f" + (i + 1), lwjglF[i]);
        int[] lwjglLetters = {0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32,
                0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C};
        for (int i = 0; i < 26; i++) {
            String letter = String.valueOf((char) ('A' + i));
            put(letter, letter.toLowerCase(Locale.ROOT), lwjglLetters[i]);
        }
        int[] lwjglDigits = {0x0B, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A};
        for (int i = 0; i < 10; i++) put(String.valueOf(i), String.valueOf(i), lwjglDigits[i]);
        int[] lwjglKp = {0x52, 0x4F, 0x50, 0x51, 0x4B, 0x4C, 0x4D, 0x47, 0x48, 0x49};
        for (int i = 0; i < 10; i++) put("KP_" + i, "keypad." + i, lwjglKp[i]);
        put("RIGHT_ALT", "right.alt", 0xB8);
        put("LEFT_ALT", "left.alt", 0x38);
        put("RIGHT_CONTROL", "right.control", 0x9D);
        put("GRAVE", "grave.accent", 0x29);
        put("INSERT", "insert", 0xD2);
        put("DELETE", "delete", 0xD3);
        put("HOME", "home", 0xC7);
        put("END", "end", 0xCF);
        put("PAGE_UP", "page.up", 0xC9);
        put("PAGE_DOWN", "page.down", 0xD1);
    }

    private Keys() {}

    private static void put(String name, String mcSuffix, int lwjgl2) {
        KEYS.put(name, new Key("key.keyboard." + mcSuffix, lwjgl2));
    }

    /** @return the key, or null for "NONE"/unknown names */
    public static Key byName(String name) {
        return KEYS.get(name.trim().toUpperCase(Locale.ROOT));
    }
}
