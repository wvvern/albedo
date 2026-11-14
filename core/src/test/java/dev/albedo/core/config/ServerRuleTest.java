package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRuleTest {

    @Test
    void serverNamesAreNormalizedAndMatchTheirSubdomains() {
        assertEquals("mc.hypixel.net", ServerRule.host(" MC.Hypixel.net.:25565 "));
        assertEquals("2001:db8::1", ServerRule.host("[2001:db8::1]:25565"));
        assertEquals("2001:db8::1", ServerRule.host("2001:db8::1"));
        assertEquals("", ServerRule.host(null));

        Config c = Config.defaults()
                .withServer(new ServerRule("hypixel.net", "quake-black"))
                .withServer(new ServerRule("eu.hypixel.net", null))
                .withServer(new ServerRule("0.1", "minimal"));
        assertEquals("quake-black", c.serverRule("mc.hypixel.net:25565").profile());
        assertNull(c.serverRule("myhypixel.net"), "only whole name parts match");
        assertNull(c.serverRule("eu.hypixel.net").profile(), "the longest name wins");
        assertNull(c.serverRule("10.0.0.1"), "an IP address matches only itself");
        assertNull(c.serverRule(null));
    }

    @Test
    void serverRulesFollowTheirProfiles() {
        Config c = Config.defaults().withImportedProfile(Presets.all().get("minimal").renamed("mine"))
                .withServer(new ServerRule("hypixel.net", "mine"));
        assertEquals("ours", c.withRenamedProfile("mine", "ours").serverRule("hypixel.net").profile());
        assertNull(c.withoutProfile("mine").serverRule("hypixel.net"), "a rule goes with its profile");
        Config builtIn = Config.defaults().withServer(new ServerRule("hypixel.net", "quake-black"));
        Profile changed = builtIn.profiles().get("quake-black").with(Toggle.FULLBRIGHT, false);
        assertEquals("quake-black-custom", builtIn.withProfile(changed).serverRule("hypixel.net").profile(),
                "a rule follows a built-in profile into its copy");
        assertEquals(1, c.withServer(new ServerRule("Hypixel.net:25565", null)).servers().size(), "one rule for each server");
    }

    @Test
    void serverRulesInTheConfigFile() {
        Config c = Config.defaults().withServer(new ServerRule("hypixel.net", "quake-black")).withServer(new ServerRule("example.org", null));
        String json = ConfigCodec.encode(c);
        assertTrue(json.contains("\"example.org\": false"), json);
        assertEquals(c, ConfigCodec.decode(json));
        assertFalse(ConfigCodec.encode(Config.defaults()).contains("servers"), "a file without rules stays readable for 0.1.0");

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"servers\": { \"hypixel.net\": \"nope\" } }"));
        assertTrue(unknown.getMessage().contains("servers.hypixel.net"), unknown.getMessage());
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"servers\": { \"Hypixel.net\": false, \"hypixel.net:25565\": false } }"));
        assertTrue(twice.getMessage().contains("same server"), twice.getMessage());
    }
}
