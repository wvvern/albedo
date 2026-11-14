package dev.albedo.core.config;

import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import dev.albedo.core.texture.TextureKey;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static dev.albedo.core.config.ConfigFixtures.recolor;
import static dev.albedo.core.config.ConfigFixtures.solid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearanceCodecTest {
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    void theConfigFileHasTheFieldsOnlyWhereRecolorUsesThem() {
        Appearance category = recolor(0xFFC0A080, 80, 0, 50);
        Rule ore = new Rule("Ores", Collections.singletonList("*_ore"), category, true).withLook(recolor(0xFF3050C0, 60, 30, 0), category);
        Config withRecolor = ConfigFixtures.with(Category.BLOCKS, new CategorySettings(category, false, Collections.singletonList(ore)));
        String json = ConfigCodec.encode(withRecolor);
        assertTrue(json.contains("\"detail\": 80") && json.contains("\"keepLight\": 50"), json);
        assertEquals(withRecolor, ConfigCodec.decode(json));

        CategorySettings plain = new CategorySettings(recolor(0xFFC0A080, 80, 0, 50).withMode(Appearance.Mode.SOLID), false,
                Collections.<Rule>emptyList());
        String old = ConfigCodec.encode(ConfigFixtures.with(Category.BLOCKS, plain));
        assertFalse(old.contains("detail"), "a file without Recolor stays readable for older versions: " + old);
    }

    @Test
    void theConfigFileRejectsBadRecolorValues() {
        for (String bad : new String[]{"80.5", "101", "-1", "\"high\""}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                    "{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"recolor\", \"detail\": " + bad + " } } } }"));
            assertTrue(e.getMessage().contains("x.blocks"), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"sky\": { \"mode\": \"solid\", \"detail\": 50 } } } }"));
    }

    @Test
    void edgeChromaIsAChromaOptionInTheConfig() {
        Config c = ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"chroma\": 50, \"chromaEdge\": true } } } }");
        Appearance look = c.profiles().get("x").get(Category.BLOCKS).appearance();
        assertTrue(look.chroma().edge());
        assertTrue(ConfigCodec.encode(c).contains("\"chromaEdge\": true"), ConfigCodec.encode(c));
        assertEquals(look, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("x").get(Category.BLOCKS).appearance());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"entities\": { \"mode\": \"solid\", \"chroma\": 50, \"chromaEdge\": true } } } }"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"chroma\": 50, \"chromaEdge\": 1 } } } }"));
        assertNotEquals(Chroma.of(50), Chroma.of(50).withEdge(true));
        assertTrue(Chroma.of(50).withEdge(true).withStrength(20).withOptions(0, 10, 10).edge(), "the other options keep Edge");
    }

    @Test
    void effectsGoIntoTheFileOnlyWhereALookUsesThem() {
        Config used = ConfigFixtures.with(Category.BLOCKS, new CategorySettings(solid(WHITE, 40, 0, 70), false, Collections.<Rule>emptyList()));
        String json = ConfigCodec.encode(used);
        assertTrue(json.contains("\"bevel\": 40") && json.contains("\"chroma\": 70"), json);
        assertEquals(used, ConfigCodec.decode(json));

        Config unused = ConfigFixtures.with(Category.BLOCKS,
                new CategorySettings(Appearance.ORIGINAL.withEffects(40, 0, Chroma.OFF), false, Collections.<Rule>emptyList()));
        assertFalse(ConfigCodec.encode(unused).contains("bevel"), "Vanilla keeps the file readable for 0.2.0");
    }

    @Test
    void aGroupCanTurnOffABevelThatItsCategoryHas() {
        CategorySettings cs = new CategorySettings(solid(WHITE, 40, 0, 0), false, Collections.<Rule>emptyList());
        Rule glass = new Rule("Glass", Collections.singletonList("glass*"), solid(WHITE, 40, 0, 0), false)
                .withLook(solid(WHITE, 0, 0, 0), cs.appearance());
        Config c = ConfigFixtures.with(Category.BLOCKS, cs.withRuleAt(0, glass));
        String json = ConfigCodec.encode(c);
        assertTrue(json.contains("\"bevel\": 0"), json);
        Policy policy = Policy.of(ConfigCodec.decode(json));
        assertEquals(0, policy.appearanceFor(new TextureKey(Category.BLOCKS, "minecraft:glass")).bevel());
        assertEquals(40, policy.appearanceFor(new TextureKey(Category.BLOCKS, "minecraft:stone")).bevel());
    }

    @Test
    void effectKeysWhereTheyHaveNoMeaningAreErrors() {
        IllegalArgumentException sky = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"sky\": { \"mode\": \"solid\", \"chroma\": 50 } } } }"));
        assertTrue(sky.getMessage().contains("x.sky"), sky.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"items\": { \"mode\": \"solid\", \"bevel\": 50 } } } }"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"outline\": 101 } } } }"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"entities\": { \"mode\": \"solid\", \"outline\": 50 } } } }"));
        Config items = ConfigCodec.decode("{ \"profiles\": { \"x\": { \"items\": { \"mode\": \"solid\", \"outline\": 50 } } } }");
        assertEquals(50, items.profiles().get("x").get(Category.ITEMS).appearance().outline());
    }
}
