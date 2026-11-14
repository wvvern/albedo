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

    @Test
    void aVisitWithoutARuleSavesTheChoiceAndKeepsTheStoredConfig() {
        ServerVisit visit = new ServerVisit();
        Config stored = withOwnProfile();
        assertTrue(visit.join("example.org"));
        assertFalse(visit.join("example.org"), "the same host is no new visit");
        assertSame(stored, visit.effective(stored));
        Config.State off = new Config.State(false, "test");
        assertEquals(off, visit.choose(stored, off).state(), "the file takes the choice");
        assertSame(stored, visit.effective(stored));
    }

    @Test
    void aChoiceOnARuleServerLastsUntilThePlayerLeavesOrTheRuleGoes() {
        ServerVisit visit = new ServerVisit();
        Config stored = withOwnProfile().withServer(new ServerRule("hypixel.net", "quake-black"));
        visit.join("hypixel.net");
        assertEquals("quake-black", visit.effective(stored).activeProfileName());
        assertSame(stored, visit.choose(stored, new Config.State(true, "minimal")), "the file keeps its state");
        assertEquals("minimal", visit.effective(stored).activeProfileName());

        visit.join(null);
        assertNull(visit.host());
        assertSame(stored, visit.effective(stored));
        visit.join("hypixel.net");
        assertEquals("quake-black", visit.effective(stored).activeProfileName(), "a new visit forgets the choice");

        visit.choose(stored, new Config.State(true, "minimal"));
        Config withoutRule = stored.withoutServer("hypixel.net");
        assertSame(withoutRule, visit.effective(withoutRule));
        assertEquals("quake-black", visit.effective(stored).activeProfileName(), "a removed rule took the choice");
    }

    @Test
    void aRuleWithoutAProfileTurnsTheModOff() {
        ServerVisit visit = new ServerVisit();
        visit.join("example.org");
        assertFalse(visit.effective(withOwnProfile().withServer(new ServerRule("example.org", null))).enabled());
    }
}
