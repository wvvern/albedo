package dev.albedo.core.config;

import dev.albedo.core.Category;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCodecTest {

    @Test
    void badConfigReportsLocation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"color\": \"nope\" } } } }"));
        assertTrue(e.getMessage().contains("x.blocks"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"bloks\": {} } } }"));
        IllegalArgumentException typo = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"rules\": [] } } } }"));
        assertTrue(typo.getMessage().contains("'rules'"), typo.getMessage());
        IllegalArgumentException mode = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"texture\" } } } }"));
        assertTrue(mode.getMessage().startsWith("x.blocks: 'mode': use one of [original, "), mode.getMessage());
        IllegalArgumentException key = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"texture\": \"a.png\" } } } }"));
        assertTrue(key.getMessage().contains("x.blocks: unknown setting 'texture'"), key.getMessage());

        // A group's own "keep" shape survives a round trip under an "opaque" category.
        Appearance opaqueLook = new Appearance(Appearance.Mode.SOLID, 0xFFFFFFFF, Appearance.Alpha.OPAQUE);
        Rule keep = new Rule("Glass", Collections.singletonList("*glass*"), opaqueLook, true)
                .withLook(opaqueLook.withAlpha(Appearance.Alpha.KEEP), opaqueLook);
        Config withKeep = ConfigFixtures.with(Category.BLOCKS, new CategorySettings(opaqueLook, false, Collections.singletonList(keep)));
        assertEquals(withKeep, ConfigCodec.decode(ConfigCodec.encode(withKeep)));
    }

    @Test
    void aNullValueIsMissingExceptForAChoice() {
        Config c = ConfigCodec.decode("{ \"enabled\": null, \"activeProfile\": null, \"keys\": { \"toggle\": null },"
                + " \"profiles\": { \"x\": { \"fullbright\": null, \"blocks\": { \"mode\": \"recolor\", \"detail\": null } } } }");
        assertTrue(c.enabled());
        assertEquals(Config.KeyBinds.DEFAULT, c.keys());
        assertEquals(Appearance.ORIGINAL.detail(), c.profiles().get("x").get(Category.BLOCKS).appearance().detail());
        IllegalArgumentException mode = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": null } } } }"));
        assertTrue(mode.getMessage().startsWith("x.blocks: 'mode': use one of [original, "), mode.getMessage());
    }

    @Test
    void aValueOfTheWrongTypeNamesTheKey() {
        assertEquals("'enabled': use true or false",
                assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("{ \"enabled\": 1 }")).getMessage());
        assertEquals("'activeProfile': use a string",
                assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("{ \"activeProfile\": 1 }")).getMessage());
        assertEquals("x.blocks: 'detail': use a whole number from 0 to 100", assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"recolor\", \"detail\": 2.5 } } } }"))
                .getMessage());
    }

    @Test
    void profileFilesRoundTripAndRejectBadInput() {
        Config c = ConfigCodec.decode("{ \"activeProfile\": \"Mine\", \"profiles\": { \"Mine\": { \"shadows\": false,"
                + " \"blocks\": { \"mode\": \"solid\", \"onlyGroups\": true,"
                + " \"groups\": [ { \"name\": \"Ground\", \"match\": [\"dirt\", \"gravel\"] } ] } } } }");
        for (Profile p : Config.defaults().withProfile(c.activeProfile()).profiles().values()) {
            assertEquals(p.renamed(Config.normalizeName(p.name())), ConfigCodec.decodeProfileFile(ConfigCodec.encodeProfileFile(p)));
        }
        Profile imported = ConfigCodec.decodeProfileFile(ConfigCodec.encodeProfileFile(c.activeProfile()));
        assertEquals("mine", imported.name(), "names are normalized");
        assertTrue(imported.get(Category.BLOCKS).rules().get(0).usesGlobalLook(), "global-look groups stay linked");

        Config twice = Config.defaults().withImportedProfile(imported).withImportedProfile(imported);
        assertEquals("mine-2", twice.activeProfileName(), "an import never replaces a profile");
        assertEquals(Config.defaults().profiles().get("quake-white"),
                Config.defaults().withImportedProfile(Config.defaults().activeProfile()).profiles().get("quake-white"));

        String[][] bad = {
            {"{ \"enabled\": true }", "not an Albedo profile"},
            {"[1]", "not an Albedo profile"},
            {"{ \"albedoProfile\": 2, \"name\": \"x\" }", "version 2"},
            {"{ \"albedoProfile\": 1 }", "no \"name\""},
            {"{ \"albedoProfile\": 1, \"name\": \"x\", \"extra\": 1 }", "'extra'"},
            {"{ \"albedoProfile\": 1, \"name\": \"x\", \"profile\": { \"sky\": { \"mode\": \"average\" } } }", "x.sky"},
        };
        for (String[] b : bad) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decodeProfileFile(b[0]), b[0]);
            assertTrue(e.getMessage().contains(b[1]), e.getMessage());
        }
    }
}
