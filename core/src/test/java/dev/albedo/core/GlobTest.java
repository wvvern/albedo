package dev.albedo.core;

import dev.albedo.core.config.ConfigCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobTest {

    @Test
    void patternsAreGlobsOrRegularExpressions() {
        assertTrue(Glob.compile("stone").matches("minecraft:stone"));
        assertFalse(Glob.compile("stone").matches("minecraft:stone_bricks"), "a glob matches the whole name");
        assertTrue(Glob.compile("*").matches("minecraft:flame"));
        assertTrue(Glob.compile("/^(oak|birch)_/").matches("minecraft:oak_leaves"), "the namespace is not part of the match");
        assertFalse(Glob.compile("/^(oak|birch)_/").matches("minecraft:dark_oak_leaves"));
        assertTrue(Glob.compile("/LEAVES$/").matches("minecraft:oak_leaves"), "case does not matter");
        assertTrue(Glob.compile("/^mymod:/").matches("mymod:ruby_ore"), "a ':' brings the namespace in");
        assertTrue(Glob.compile("/generic_\\d/").matches("minecraft:generic_7"));
        assertNull(Glob.problem("/ok/"));
        assertNull(Glob.problem("plain"));
        assertNotNull(Glob.problem("/(open/"));
        assertNotNull(Glob.problem("//"), "an empty expression would match everything");
        assertTrue(Glob.compile("/^(?:oak)_log/").matches("minecraft:oak_log"), "(?: is not a namespace");
        assertTrue(Glob.compile("/(?i:OAK)_log/").matches("minecraft:oak_log"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"particles\": { \"groups\": [ { \"match\": \"/(open/\" } ] } } } }"));
        assertTrue(e.getMessage().contains("not a valid pattern"), e.getMessage());
    }
}
