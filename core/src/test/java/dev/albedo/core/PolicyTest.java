package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.ConfigFixtures;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.texture.TextureClassifier;
import dev.albedo.core.texture.TextureKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyTest {

    @Test
    void configRoundTripsAndRulesWin() {
        String json = "{\n" +
                "  // comments are allowed\n" +
                "  \"activeProfile\": \"mine\",\n" +
                "  \"profiles\": { \"mine\": {\n" +
                "    \"fullbright\": true,\n" +
                "    \"blocks\": { \"mode\": \"solid\", \"color\": \"#222\", \"removeTint\": true,\n" +
                "      \"groups\": [ { \"name\": \"Glass\", \"match\": [\"*glass*\", \"minecraft:ice\"], \"color\": \"#80FFFFFF\" },\n" +
                "                    { \"match\": \"*_ore\", \"mode\": \"original\" } ] },\n" +
                "    \"sky\": { \"mode\": \"solid\", \"color\": \"black\" },\n" +
                "  } }\n" +
                "}";
        Config c = ConfigCodec.decode(json);
        Policy p = Policy.of(c);
        assertEquals(0xFF222222, p.appearanceFor(TextureClassifier.classify("minecraft:block/stone")).color());
        assertEquals(0x80FFFFFF, p.appearanceFor(TextureClassifier.classify("minecraft:block/glass_pane_top")).color());
        assertTrue(p.appearanceFor(TextureClassifier.classify("minecraft:block/diamond_ore")).isOriginal());
        assertTrue(p.removeTint(Category.BLOCKS));
        assertEquals(Policy.FULLBRIGHT_GAMMA, p.gamma(0.5), 0.0);
        assertTrue(p.shadows(), "toggles not mentioned keep their defaults");
        assertTrue(p.overridesSky());
        assertArrayEquals(new float[]{0f, 0f, 0f}, p.skyRgb(), 0f);
        assertEquals(0.5, Policy.of(Config.defaults().withActiveProfile("minimal")).gamma(0.5), 0.0);
        assertEquals(Appearance.Mode.HIDDEN, p.appearanceFor(TextureClassifier.classify("minecraft:textures/environment/sun.png")).mode());
        assertEquals(Appearance.Mode.SOLID, p.appearanceFor(TextureClassifier.classify("minecraft:textures/environment/end_sky.png")).mode());
        assertEquals(0xFFFFFFFF, p.appearanceFor(TextureClassifier.classify("minecraft:textures/colormap/grass.png")).color());
        assertTrue(p.appearanceFor(TextureClassifier.classify("minecraft:textures/entity/zombie/zombie.png")).isOriginal());

        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)));
    }

    @Test
    void lightingAndShadowsDoNotChangeTextures() {
        // Looks, foliage and the on/off switch change textures.
        Config c = Config.defaults();
        Rule ground = new Rule("Ground", Arrays.asList("dirt", "grass_block*", "gravel"), Appearance.solid(0xFF7A5C3A), false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS).withRules(Collections.singletonList(ground));
        Policy p = Policy.of(c);
        assertTrue(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Toggle.FULLBRIGHT, false)))));
        assertTrue(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Toggle.SHADING, false)))));
        assertFalse(p.sameTextures(Policy.of(c.withEnabled(false))));
        assertFalse(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks)))));
        assertFalse(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Toggle.FOLIAGE, true)))));
        assertTrue(Policy.DISABLED.sameTextures(Policy.of(c.withEnabled(false))));
    }

    @Test
    void foliageHidesPlantsButNotLeavesOrGrassBlocks() {
        Policy hidden = Policy.of(Config.defaults()); // quake-white hides foliage
        for (String plant : new String[]{"block/short_grass", "block/tall_grass_top", "block/poppy", "block/oak_sapling",
                "block/large_fern_bottom", "blocks/tallgrass", "blocks/flower_rose", "blocks/double_plant_grass_top"}) {
            assertEquals(Appearance.Mode.HIDDEN, hidden.appearanceFor(TextureClassifier.classify("minecraft:" + plant)).mode(), plant);
        }
        for (String block : new String[]{"block/grass_block_top", "block/oak_leaves", "blocks/grass_side", "block/stone"}) {
            assertNotEquals(Appearance.Mode.HIDDEN, hidden.appearanceFor(TextureClassifier.classify("minecraft:" + block)).mode(), block);
        }
        Policy shown = Policy.of(Config.defaults().withActiveProfile("minimal"));
        assertEquals(Appearance.Mode.AVERAGE, shown.appearanceFor(TextureClassifier.classify("minecraft:block/poppy")).mode());
        assertTrue(shown.shading());
    }

    @Test
    void skyEditsDoNotRepaintTexturesButTheSunSwitchDoes() {
        Profile base = Config.defaults().activeProfile();
        CategorySettings sky = ConfigFixtures.layers(SkyPresets.DUSK);
        Policy a = Policy.of(base.with(Category.SKY, sky));
        Policy b = Policy.of(base.with(Category.SKY, sky.withSky(SkyPresets.DUSK.withLayer(0,
                SkyPresets.DUSK.layers().get(0).withOpacity(40)))));
        Policy c = Policy.of(base.with(Category.SKY, sky.withSky(SkyPresets.DUSK.withCelestial(SkyDesign.Celestial.HIDE))));
        Policy d = Policy.of(base.with(Category.SKY, sky.withSky(SkyPresets.DUSK.withExtent(SkyDesign.Extent.SPHERE))));
        assertTrue(a.sameTextures(b));
        assertTrue(a.sameTextures(d), "the extent changes no texture");
        assertFalse(a.sameTextures(c));
        TextureKey sun = new TextureKey(Category.SKY, "minecraft:textures/environment/sun.png");
        TextureKey end = new TextureKey(Category.SKY, "minecraft:textures/environment/end_sky.png");
        assertEquals(Appearance.Mode.ORIGINAL, a.appearanceFor(sun).mode());
        assertEquals(Appearance.Mode.HIDDEN, c.appearanceFor(sun).mode());
        assertEquals(Appearance.Mode.ORIGINAL, c.appearanceFor(end).mode());
        assertEquals(Policy.SkyStyle.LAYERS, a.skyStyle());
        assertNull(a.skyRgb(), "the flat sky color is only for Color");
        assertFalse(Appearance.of(Appearance.Mode.LAYERS).changesPixels());
    }
}
