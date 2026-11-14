package dev.albedo.core.config;

import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void editingOperations() {
        Config c = Config.defaults();
        assertEquals("quake-black", c.profileNameAt(1));
        assertEquals("pastel", c.profileNameAt(-1));
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)));

        Rule ground = new Rule("Ground", Arrays.asList("dirt", "grass_block*", "gravel"), Appearance.solid(0xFF7A5C3A), false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS).withRules(Collections.singletonList(ground));
        assertEquals(0xFF7A5C3A, look(blocks, "minecraft:grass_block_side").color());
        assertEquals(0xFFFFFFFF, look(blocks, "minecraft:stone").color());

        // The preset's solid-leaves rule follows a new blocks color but keeps its own (opaque) shape.
        CategorySettings red = c.activeProfile().get(Category.BLOCKS).withAppearance(Appearance.solid(0xFFFF0000));
        Appearance leaves = look(red, "minecraft:oak_leaves");
        assertEquals(0xFFFF0000, leaves.color());
        assertEquals(Appearance.Alpha.OPAQUE, leaves.alpha());

        // Built-in profiles cannot be renamed or deleted; a change to one goes into a new profile.
        assertSame(c, c.withRenamedProfile("quake-white", "mine"));
        assertSame(c, c.withoutProfile("minimal"));
        Config changed = c.withProfile(c.activeProfile().with(Toggle.SHADOWS, false));
        assertEquals("quake-white-custom", changed.activeProfileName());
        // A second change to the built-in profile makes a second copy; the first copy stays as it was.
        Config again = changed.withActiveProfile("quake-white");
        again = again.withProfile(again.activeProfile().with(Toggle.FULLBRIGHT, false));
        assertEquals("quake-white-custom-2", again.activeProfileName());
        assertFalse(again.activeProfile().is(Toggle.FULLBRIGHT));
        assertFalse(again.profiles().get("quake-white-custom").is(Toggle.SHADOWS));
        assertTrue(again.profiles().get("quake-white-custom").is(Toggle.FULLBRIGHT));
        assertEquals(Presets.all().get("quake-white"), changed.profiles().get("quake-white"));
        assertEquals(Collections.singleton("quake-white-custom"), changed.userProfiles().keySet());
        assertEquals(changed, ConfigCodec.decode(ConfigCodec.encode(changed)), "the file holds only the user profiles");
        assertFalse(ConfigCodec.encode(changed).contains("\"quake-white\": {"));

        Config renamed = changed.withRenamedProfile("quake-white-custom", "mine");
        assertEquals("mine", renamed.activeProfileName());
        assertEquals("mine", renamed.activeProfile().name());
        assertEquals("quake-white-2", c.freeProfileName("quake-white"));
        assertEquals("fresh", c.freeProfileName("fresh"));
        assertEquals(c.profiles(), renamed.withoutProfile("mine").profiles());

        // An old file that stored a changed built-in profile keeps the change as a new profile.
        Config old = ConfigCodec.decode("{ \"activeProfile\": \"minimal\", \"profiles\": { \"minimal\": { \"fullbright\": true } } }");
        assertEquals("minimal-custom", old.activeProfileName());
        assertTrue(old.activeProfile().is(Toggle.FULLBRIGHT));
    }

    /** True when the policy draws the player part that {@code toggle} switches. */
    private static boolean shows(Policy policy, Toggle toggle) {
        return toggle == Toggle.ARMOR ? policy.playerArmor() : policy.playerCape();
    }

    @ParameterizedTest
    @EnumSource(value = Toggle.class, names = {"ARMOR", "CAPE"})
    void playerToggle(Toggle toggle) {
        // A file from before the toggle has no key for it, so the part stays shown, in every built-in profile too.
        Config old = ConfigCodec.decode("{ \"activeProfile\": \"Mine\", \"profiles\": { \"Mine\": { \"shadows\": false } } }");
        assertTrue(old.activeProfile().is(toggle));
        assertTrue(shows(Policy.of(old), toggle));
        for (Profile p : Config.defaults().profiles().values()) assertTrue(p.is(toggle), p.name());
        assertFalse(ConfigCodec.encode(old).contains("\"" + toggle.key + "\""), "a default toggle is not written");

        Config hidden = old.withProfile(old.activeProfile().with(toggle, false));
        assertFalse(shows(Policy.of(hidden), toggle));
        assertTrue(ConfigCodec.encode(hidden).contains("\"" + toggle.key + "\": false"));
        Config again = ConfigCodec.decode(ConfigCodec.encode(hidden));
        assertEquals(hidden, again);
        assertFalse(again.activeProfile().is(toggle));
        assertFalse(again.activeProfile().is(Toggle.SHADOWS), "the other toggles keep their values");

        assertTrue(shows(Policy.of(hidden.withEnabled(false)), toggle), "with the mod off, the part shows");
        assertTrue(shows(Policy.DISABLED, toggle));
        assertTrue(Policy.of(old).sameTextures(Policy.of(hidden)), "the toggle does not change textures");
        assertSame(toggle, Toggle.byKey(toggle.key));
    }

    @Test
    void armorAndCapeHaveTheirOwnToggles() {
        Config old = ConfigCodec.decode("{ \"activeProfile\": \"Mine\", \"profiles\": { \"Mine\": { \"armor\": false } } }");
        Config hidden = old.withProfile(old.activeProfile().with(Toggle.CAPE, false));
        assertFalse(Policy.of(hidden).playerArmor(), "armor keeps its own value");
        assertTrue(Policy.of(old.withProfile(old.activeProfile().with(Toggle.ARMOR, true))).playerCape(), "armor does not hide capes");
    }

    @Test
    void groupsWhitelistAndBlacklist() {
        Appearance white = Appearance.solid(0xFFFFFFFF);
        Rule keep = new Rule("Ores", Collections.singletonList("*_ore"), Appearance.ORIGINAL, false);
        Rule globalLook = new Rule("Ground", Arrays.asList("dirt", "gravel"), white, false).withGlobalLook();

        // Everything: the global look everywhere, a vanilla group excludes (blacklist).
        CategorySettings everything = new CategorySettings(white, false, false, Collections.singletonList(keep));
        assertTrue(look(everything, "minecraft:diamond_ore").isOriginal());
        assertEquals(white, look(everything, "minecraft:stone"));
        assertEquals(keep, everything.blocksScopeSwitch(), "a vanilla group means nothing under 'only groups'");

        // Only groups: unlisted stays vanilla, a global-look group takes the look (whitelist).
        CategorySettings only = new CategorySettings(white, false, true, Collections.singletonList(globalLook));
        assertTrue(look(only, "minecraft:stone").isOriginal());
        assertEquals(white, look(only, "minecraft:gravel"));
        assertEquals(globalLook, only.blocksScopeSwitch(), "a global-look group means nothing under 'everything'");

        // Both survive the file, including the "global look" (no look fields) and "onlyGroups".
        Config withGroups = ConfigFixtures.with(Category.BLOCKS, only);
        Config decoded = ConfigCodec.decode(ConfigCodec.encode(withGroups));
        assertEquals(withGroups, decoded);
        assertTrue(decoded.activeProfile().get(Category.BLOCKS).rules().get(0).usesGlobalLook());
    }

    private static Appearance look(CategorySettings settings, String id) {
        return settings.lookOf(settings.ruleFor(id));
    }
}
