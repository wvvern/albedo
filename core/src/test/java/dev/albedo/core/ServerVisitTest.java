package dev.albedo.core;

import dev.albedo.core.config.Config;
import dev.albedo.core.config.ServerRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.albedo.core.CoreFixture.playOn;
import static dev.albedo.core.CoreFixture.saved;
import static dev.albedo.core.CoreFixture.withOwnProfile;
import static org.junit.jupiter.api.Assertions.*;

/** The server rules: they change the config in effect for a visit, and the file keeps the player's own choice. */
class ServerVisitTest {
    private final Albedo albedo = CoreFixture.albedo();

    @BeforeEach
    void defaults() {
        CoreFixture.reset();
    }

    @Test
    void aServerRuleSetsTheProfileOnlyWhileThePlayerIsThere() throws Exception {
        albedo.apply(withOwnProfile().withServer(new ServerRule("hypixel.net", "quake-black")));
        playOn("mc.hypixel.net:25565");
        assertEquals("quake-black", albedo.config().activeProfileName());
        assertEquals("test", saved().activeProfileName(), "the file keeps the player's own profile");

        albedo.apply(albedo.storedConfig().withServer(new ServerRule("example.org", null)));
        assertEquals("quake-black", albedo.config().activeProfileName(), "an edit to the file keeps the rule");
        assertEquals("test", saved().activeProfileName());

        playOn(null);
        assertEquals("test", albedo.config().activeProfileName());
    }

    @Test
    void aProfileChoiceOnAServerLastsOnlyForTheVisit() throws Exception {
        albedo.apply(withOwnProfile().withServer(new ServerRule("hypixel.net", "quake-black")));
        playOn("mc.hypixel.net");
        albedo.apply(albedo.storedConfig(), new Config.State(true, "minimal")); // F7 and the profile list do this
        assertEquals("minimal", albedo.config().activeProfileName());
        albedo.toggle();
        assertFalse(albedo.config().enabled(), "F6 also changes only the visit");
        assertEquals("test", saved().activeProfileName(), "the file keeps the player's own profile");
        assertTrue(saved().enabled());
        playOn(null);
        assertEquals("test", albedo.config().activeProfileName());
        assertTrue(albedo.config().enabled());
        playOn("mc.hypixel.net");
        assertEquals("quake-black", albedo.config().activeProfileName(), "the rule applies again on the next visit");
    }

    @Test
    void offStaysOffAfterTheRuleOfTheServerIsRemoved() throws Exception {
        albedo.apply(withOwnProfile().withServer(new ServerRule("hypixel.net", "quake-black")));
        playOn("mc.hypixel.net");
        albedo.apply(albedo.storedConfig(), new Config.State(true, "minimal"));
        albedo.apply(albedo.storedConfig().withoutServer("hypixel.net"));
        albedo.apply(albedo.storedConfig(), new Config.State(false, "test")); // the On switch and Apply do this
        assertFalse(albedo.config().enabled(), "off in the world");
        assertFalse(saved().enabled(), "off in the file");
    }

    @Test
    void aNewRuleForTheCurrentServerAppliesAtOnce() throws Exception {
        albedo.apply(withOwnProfile());
        playOn("example.org");
        albedo.apply(albedo.storedConfig().withServer(new ServerRule("example.org", null)));
        assertFalse(albedo.config().enabled(), "off on this server");
        assertTrue(saved().enabled(), "and on everywhere else");
    }
}
