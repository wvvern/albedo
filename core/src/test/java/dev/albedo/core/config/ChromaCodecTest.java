package dev.albedo.core.config;

import dev.albedo.core.Category;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaCodecTest {
    private static Config withBlocks(CategorySettings blocks) {
        Config c = Config.defaults();
        return c.withProfile(c.activeProfile().renamed("mine").with(Category.BLOCKS, blocks)).withActiveProfile("mine");
    }

    private static Appearance solid(Chroma chroma) {
        return Appearance.solid(0xFFFFFFFF).withEffects(0, 0, chroma);
    }

    private static Appearance blocks(String json) {
        return ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": " + json + " } }, \"activeProfile\": \"x\" }")
                .activeProfile().get(Category.BLOCKS).appearance();
    }

    @Test
    void theChromaOptionsGoIntoTheFileAndBack() {
        Config c = withBlocks(new CategorySettings(solid(new Chroma(60, 80, 70, 20)), false, Collections.<Rule>emptyList()));
        String json = ConfigCodec.encode(c);
        assertTrue(json.contains("\"chromaSpeed\": 80") && json.contains("\"chromaSaturation\": 70")
                && json.contains("\"chromaBrightness\": 20"), json);
        assertEquals(c, ConfigCodec.decode(json));
    }

    @Test
    void onlyOptionsThatDifferFromTheParentGoIntoTheFile() {
        String defaults = ConfigCodec.encode(withBlocks(new CategorySettings(solid(Chroma.of(60)), false, Collections.<Rule>emptyList())));
        assertFalse(defaults.contains("chromaSpeed"), defaults);

        Chroma own = new Chroma(60, 80, 50, 50);
        CategorySettings cs = new CategorySettings(solid(own), false, Collections.<Rule>emptyList());
        Rule same = new Rule("Same", Collections.singletonList("stone"), solid(own.withStrength(30)), EnumSet.of(Appearance.Field.CHROMA));
        Config c = withBlocks(cs.withRuleAt(0, same));
        String json = ConfigCodec.encode(c);
        assertEquals(1, json.split("chromaSpeed").length - 1, "the group inherits the speed of its category: " + json);
        assertEquals(c, ConfigCodec.decode(json));
    }

    @Test
    void theOptionsOfAChromaThatIsOffAreNotWrittenAndNotAnError() {
        Config off = withBlocks(new CategorySettings(solid(new Chroma(0, 80, 70, 20)), false, Collections.<Rule>emptyList()));
        assertFalse(ConfigCodec.encode(off).contains("chromaSpeed"), "Chroma off keeps the file readable for older versions");

        // A hand edit that turns Chroma off but keeps an option must not reset all settings.
        Appearance a = blocks("{ \"mode\": \"solid\", \"chroma\": 0, \"chromaSpeed\": 80 }");
        assertEquals(Chroma.OFF, a.chroma());

        // An ignored option also does not make Chroma a field of its own, so the group keeps following its category.
        Rule group = ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"groups\": "
                + "[ { \"match\": \"stone\", \"mode\": \"recolor\", \"chromaSpeed\": 80 } ] } } }, \"activeProfile\": \"x\" }")
                .activeProfile().get(Category.BLOCKS).rules().get(0);
        assertEquals(EnumSet.of(Appearance.Field.MODE), group.ownFields());
    }

    @Test
    void anOldFileWithChromaGetsMovingColorsAtTheDefaults() {
        assertEquals(Chroma.of(70), blocks("{ \"mode\": \"solid\", \"chroma\": 70 }").chroma());
        assertTrue(Chroma.of(70).moves());
        assertEquals(new Chroma(70, 0, 50, 50), blocks("{ \"mode\": \"solid\", \"chroma\": 70, \"chromaSpeed\": 0 }").chroma());
    }

    @Test
    void chromaOptionsWhereChromaHasNoMeaningAreErrors() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"sky\": { \"mode\": \"solid\", \"chromaSpeed\": 50 } } } }"));
        assertTrue(e.getMessage().contains("x.sky") && e.getMessage().contains("chromaSpeed"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> blocks("{ \"mode\": \"solid\", \"chroma\": 50, \"chromaSpeed\": 101 }"));
    }
}
