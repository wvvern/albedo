package dev.blockpainter.core;

import dev.blockpainter.core.config.Appearance;
import dev.blockpainter.core.config.CategorySettings;
import dev.blockpainter.core.config.Config;
import dev.blockpainter.core.config.ConfigCodec;
import dev.blockpainter.core.config.Profile;
import dev.blockpainter.core.config.Rule;
import dev.blockpainter.core.config.Toggle;
import dev.blockpainter.core.texture.AtlasPaths;
import dev.blockpainter.core.texture.PixelTransformer;
import dev.blockpainter.core.texture.TextureClassifier;
import dev.blockpainter.core.texture.TextureKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {

    @Test
    void classifiesModernAndLegacyPaths() {
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:stone"), TextureClassifier.classify("minecraft", "textures/block/stone.png"));
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:stone"), TextureClassifier.classify("minecraft", "blocks/stone"));
        assertEquals(Category.FLUIDS, TextureClassifier.classify("minecraft:block/water_still").category());
        assertEquals(Category.FLUIDS, TextureClassifier.classify("minecraft:blocks/lava_flow").category());
        assertEquals(Category.BLOCK_BREAKING, TextureClassifier.classify("minecraft:block/destroy_stage_3").category());
        assertEquals(Category.ITEMS, TextureClassifier.classify("minecraft:items/diamond_sword").category());
        assertEquals(Category.PARTICLES, TextureClassifier.classify("minecraft:textures/particle/particles.png").category());
        assertEquals(Category.CLOUDS, TextureClassifier.classify("minecraft:textures/environment/clouds.png").category());
        assertEquals(Category.WEATHER, TextureClassifier.classify("minecraft:textures/environment/rain.png").category());
        assertEquals(Category.SKY, TextureClassifier.classify("minecraft:textures/environment/sun.png").category());
        assertEquals(Category.BLOCK_ENTITIES, TextureClassifier.classify("minecraft:textures/entity/chest/normal.png").category());
        assertEquals(Category.ENTITIES, TextureClassifier.classify("minecraft:textures/entity/zombie/zombie.png").category());
        assertEquals(Category.PLAYERS, TextureClassifier.classify("minecraft:textures/entity/player/wide/alex.png").category());
        assertEquals(Category.COLORMAPS, TextureClassifier.classify("minecraft:textures/colormap/grass.png").category());
        assertEquals(Category.OTHER, TextureClassifier.classify("minecraft:textures/gui/widgets.png").category());
        assertEquals("mymod:copper_ore", TextureClassifier.classify("MyMod", "textures/block/copper_ore.png").id());
    }

    @Test
    void atlasRestoresStrippedFolders() {
        assertEquals("minecraft:particle/flame", AtlasPaths.spriteLocation("minecraft:textures/atlas/particles.png", "minecraft:flame"));
        assertEquals("minecraft:block/stone", AtlasPaths.spriteLocation("minecraft:textures/atlas/blocks.png", "minecraft:block/stone"));
        assertEquals(Category.SKY, TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:celestials", "minecraft:moon/full_moon")).category());
        assertEquals(Category.BLOCK_ENTITIES, TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:paintings", "minecraft:kebab")).category());
    }

    @Test
    void transformsKeepShapeAndHide() {
        PixelBuffer.Heap img = new PixelBuffer.Heap(2, 1, new int[]{0xFF123456, 0x00ABCDEF});
        PixelTransformer.apply(Appearance.solid(0xFFFFFFFF), img, null);
        assertEquals(0xFFFFFFFF, img.getArgb(0, 0));
        assertEquals(0x00FFFFFF, img.getArgb(1, 0));

        PixelTransformer.apply(new Appearance(Appearance.Mode.SOLID, 0xFF808080, null, Appearance.Alpha.OPAQUE), img, null);
        assertEquals(0xFF808080, img.getArgb(1, 0));

        PixelTransformer.apply(Appearance.of(Appearance.Mode.HIDDEN), img, null);
        assertEquals(0, Colors.alpha(img.getArgb(0, 0)));

        assertEquals(0xFF7F7F7F, PixelTransformer.average(new PixelBuffer.Heap(2, 1, new int[]{0xFF000000, 0xFFFFFFFF})));
    }

    @Test
    void textureModeStretchesAndFallsBack() {
        PixelBuffer.Heap user = new PixelBuffer.Heap(1, 1, new int[]{0xFFFF0000});
        PixelBuffer.Heap img = new PixelBuffer.Heap(2, 2, new int[]{0xFF000000, 0xFF000000, 0xFF000000, 0x00000000});
        PixelTransformer.apply(new Appearance(Appearance.Mode.TEXTURE, 0, "red.png", Appearance.Alpha.KEEP), img, n -> user);
        assertEquals(0xFFFF0000, img.getArgb(0, 0));
        assertEquals(0x00FF0000, img.getArgb(1, 1));

        PixelBuffer.Heap missing = new PixelBuffer.Heap(1, 1, new int[]{0xFF000000});
        PixelTransformer.apply(new Appearance(Appearance.Mode.TEXTURE, 0xFF00FF00, null, Appearance.Alpha.KEEP), missing, n -> null);
        assertEquals(0xFF00FF00, missing.getArgb(0, 0));
    }

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
    void editingOperations() {
        Config c = Config.defaults();
        assertEquals("quake-black", c.profileNameAt(1));
        assertEquals("minimal", c.profileNameAt(-1));
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)));

        Rule ground = new Rule("Ground", Arrays.asList("dirt", "grass_block*", "gravel"), Appearance.solid(0xFF7A5C3A), false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS).withRules(Collections.singletonList(ground));
        assertEquals(0xFF7A5C3A, blocks.appearanceFor("minecraft:grass_block_side").color());
        assertEquals(0xFFFFFFFF, blocks.appearanceFor("minecraft:stone").color());

        // The preset's solid-leaves rule follows a new blocks color but keeps its own (opaque) shape.
        CategorySettings red = c.activeProfile().get(Category.BLOCKS).withAppearance(Appearance.solid(0xFFFF0000));
        Appearance leaves = red.ruleFor("minecraft:oak_leaves").appearance();
        assertEquals(0xFFFF0000, leaves.color());
        assertEquals(Appearance.Alpha.OPAQUE, leaves.alpha());

        Config renamed = c.withRenamedProfile("quake-white", "mine");
        assertEquals("mine", renamed.activeProfileName());
        assertEquals("mine", renamed.activeProfile().name());
        assertEquals("quake-white-2", c.freeProfileName("quake-white"));
        assertEquals("fresh", c.freeProfileName("fresh"));
        assertEquals(c.profiles().size() - 1, c.withoutProfile("minimal").profiles().size());

        // Only texture changes need a resource reload; lighting doesn't.
        assertTrue(c.sameRendering(c.withProfile(c.activeProfile().with(Toggle.FULLBRIGHT, false))));
        assertTrue(c.sameRendering(c.withProfile(c.activeProfile().with(Toggle.SHADOWS, false))));
        assertFalse(c.sameRendering(c.withEnabled(false)));
        assertFalse(c.sameRendering(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks))));
        assertFalse(c.sameRendering(c.withProfile(c.activeProfile().with(Toggle.SHADING, false))));
        assertFalse(c.sameRendering(c.withProfile(c.activeProfile().with(Toggle.FOLIAGE, true))));
    }

    @Test
    void groupsWhitelistAndBlacklist() {
        Appearance white = Appearance.solid(0xFFFFFFFF);
        Rule keep = new Rule("Ores", Collections.singletonList("*_ore"), Appearance.ORIGINAL, false);
        Rule globalLook = new Rule("Ground", Arrays.asList("dirt", "gravel"), white, false).withGlobalLook();

        // Everything: the global look everywhere, a vanilla group excludes (blacklist).
        CategorySettings everything = new CategorySettings(white, false, false, Collections.singletonList(keep));
        assertTrue(everything.appearanceFor("minecraft:diamond_ore").isOriginal());
        assertEquals(white, everything.appearanceFor("minecraft:stone"));
        assertEquals(keep, everything.blocksScopeSwitch(), "a vanilla group means nothing under 'only groups'");

        // Only groups: unlisted stays vanilla, a global-look group takes the look (whitelist).
        CategorySettings only = new CategorySettings(white, false, true, Collections.singletonList(globalLook));
        assertTrue(only.appearanceFor("minecraft:stone").isOriginal());
        assertEquals(white, only.appearanceFor("minecraft:gravel"));
        assertEquals(globalLook, only.blocksScopeSwitch(), "a global-look group means nothing under 'everything'");

        // Both survive the file, including the "global look" (no look fields) and "onlyGroups".
        Config c = Config.defaults();
        Config withGroups = c.withProfile(c.activeProfile().with(Category.BLOCKS, only));
        Config decoded = ConfigCodec.decode(ConfigCodec.encode(withGroups));
        assertEquals(withGroups, decoded);
        assertTrue(decoded.activeProfile().get(Category.BLOCKS).rules().get(0).usesGlobalLook());
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
    void badConfigReportsLocation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"color\": \"nope\" } } } }"));
        assertTrue(e.getMessage().contains("x.blocks"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"bloks\": {} } } }"));
        IllegalArgumentException typo = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"rules\": [] } } } }"));
        assertTrue(typo.getMessage().contains("'rules'"), typo.getMessage());

        // A group's own "keep" shape survives a round trip under an "opaque" category.
        Rule keep = new Rule("Glass", Collections.singletonList("*glass*"),
                new Appearance(Appearance.Mode.SOLID, 0xFFFFFFFF, null, Appearance.Alpha.KEEP), false);
        CategorySettings opaque = new CategorySettings(new Appearance(Appearance.Mode.SOLID, 0xFFFFFFFF, null, Appearance.Alpha.OPAQUE),
                false, Collections.singletonList(keep));
        Config c = Config.defaults();
        Config withKeep = c.withProfile(c.activeProfile().with(Category.BLOCKS, opaque));
        assertEquals(withKeep, ConfigCodec.decode(ConfigCodec.encode(withKeep)));
    }

    @Test
    void profileFilesRoundTripAndRejectBadInput() {
        Config c = ConfigCodec.decode("{ \"profiles\": { \"Mine\": { \"shadows\": false, \"blocks\": { \"mode\": \"solid\","
                + " \"onlyGroups\": true, \"groups\": [ { \"name\": \"Ground\", \"match\": [\"dirt\", \"gravel\"] } ] } } } }");
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
            {"{ \"enabled\": true }", "not a Block Painter profile"},
            {"[1]", "not a Block Painter profile"},
            {"{ \"blockpainterProfile\": 2, \"name\": \"x\" }", "version 2"},
            {"{ \"blockpainterProfile\": 1 }", "no \"name\""},
            {"{ \"blockpainterProfile\": 1, \"name\": \"x\", \"extra\": 1 }", "'extra'"},
            {"{ \"blockpainterProfile\": 1, \"name\": \"x\", \"profile\": { \"sky\": { \"mode\": \"average\" } } }", "x.sky"},
        };
        for (String[] b : bad) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decodeProfileFile(b[0]), b[0]);
            assertTrue(e.getMessage().contains(b[1]), e.getMessage());
        }
    }

    @Test
    void keysResolveForEveryEra() {
        assertEquals("key.keyboard.f6", Keys.byName("f6").minecraftName);
        assertEquals("key.keyboard.keypad.3", Keys.byName("KP_3").minecraftName);
        assertEquals(0x40, Keys.byName("F6").lwjgl2);
        assertNull(Keys.byName("NONE"));
    }
}
