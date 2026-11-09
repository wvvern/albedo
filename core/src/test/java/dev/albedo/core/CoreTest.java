package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Presets;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.ServerRule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.texture.AtlasPaths;
import dev.albedo.core.texture.PixelTransformer;
import dev.albedo.core.texture.TextureClassifier;
import dev.albedo.core.texture.TextureKey;
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
        // Things that carry text or pictures: modern and 1.8.9 names.
        for (String s : new String[]{"entity/signs/oak", "entity/signs/hanging/oak", "textures/entity/sign.png", "entity/banner/base",
            "entity/banner_base", "textures/map/map_background.png", "block/item_frame", "blocks/itemframe_background"}) {
            assertEquals(Category.DECORATIONS, TextureClassifier.classify("minecraft:" + s).category(), s);
        }
        assertEquals(Category.ENTITIES, TextureClassifier.classify("minecraft:textures/entity/zombie/zombie.png").category());
        assertEquals(Category.PLAYERS, TextureClassifier.classify("minecraft:textures/entity/player/wide/alex.png").category());
        assertEquals(Category.COLORMAPS, TextureClassifier.classify("minecraft:textures/colormap/grass.png").category());
        assertEquals(Category.OTHER, TextureClassifier.classify("minecraft:textures/gui/widgets.png").category());
        assertEquals("mymod:copper_ore", TextureClassifier.classify("MyMod", "textures/block/copper_ore.png").id());
    }

    @Test
    void classifiesOptiFinePaths() {
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:bookshelf"), TextureClassifier.classify("minecraft:mcpatcher/ctm/default/13"));
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:glass"), TextureClassifier.classify("minecraft:mcpatcher/ctm/default/5"));
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:sandstone_normal"), TextureClassifier.classify("minecraft:mcpatcher/ctm/default/66"));
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:glass_white"),
                TextureClassifier.classify("minecraft:mcpatcher/ctm/default/0_glass_white/12"));
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:stonebrick"), TextureClassifier.classify("minecraft:optifine/ctm/stonebrick/3"));
        assertEquals(Category.SKY, TextureClassifier.classify("minecraft:optifine/sky/world0/sky1.png").category());
        assertEquals(Category.ITEMS, TextureClassifier.classify("minecraft:optifine/cit/swords/sword.png").category());
        assertEquals(Category.COLORMAPS, TextureClassifier.classify("minecraft:mcpatcher/colormap/water.png").category());
        assertEquals(new TextureKey(Category.ENTITIES, "minecraft:cow/cow2"),
                TextureClassifier.classify("minecraft:optifine/random/entity/cow/cow2.png"));
        assertEquals(new TextureKey(Category.ENTITIES, "minecraft:cow/cow2"), TextureClassifier.classify("minecraft:mcpatcher/mob/cow/cow2.png"));
        assertEquals(Category.OTHER, TextureClassifier.classify("minecraft:optifine/anim/water.png").category());
    }

    @Test
    void atlasRestoresStrippedFolders() {
        assertEquals("minecraft:particle/flame", AtlasPaths.spriteLocation("minecraft:textures/atlas/particles.png", "minecraft:flame"));
        assertEquals("minecraft:block/stone", AtlasPaths.spriteLocation("minecraft:textures/atlas/blocks.png", "minecraft:block/stone"));
        assertEquals(Category.SKY,
                TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:celestials", "minecraft:moon/full_moon")).category());
        assertEquals(Category.DECORATIONS,
                TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:paintings", "minecraft:kebab")).category());
        assertEquals(Category.DECORATIONS,
                TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:map_decorations", "minecraft:player")).category());
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
        assertEquals("pastel", c.profileNameAt(-1));
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)));

        Rule ground = new Rule("Ground", Arrays.asList("dirt", "grass_block*", "gravel"), Appearance.solid(0xFF7A5C3A), false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS).withRules(Collections.singletonList(ground));
        assertEquals(0xFF7A5C3A, blocks.appearanceFor("minecraft:grass_block_side").color());
        assertEquals(0xFFFFFFFF, blocks.appearanceFor("minecraft:stone").color());

        // The preset's solid-leaves rule follows a new blocks color but keeps its own (opaque) shape.
        CategorySettings red = c.activeProfile().get(Category.BLOCKS).withAppearance(Appearance.solid(0xFFFF0000));
        Appearance leaves = red.appearanceFor("minecraft:oak_leaves");
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

        // Lighting and shadows do not change textures; looks, foliage and the on/off switch do.
        Policy p = Policy.of(c);
        assertTrue(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Toggle.FULLBRIGHT, false)))));
        assertTrue(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Toggle.SHADING, false)))));
        assertFalse(p.sameTextures(Policy.of(c.withEnabled(false))));
        assertFalse(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks)))));
        assertFalse(p.sameTextures(Policy.of(c.withProfile(c.activeProfile().with(Toggle.FOLIAGE, true)))));
        assertTrue(Policy.DISABLED.sameTextures(Policy.of(c.withEnabled(false))));
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
        Appearance opaqueLook = new Appearance(Appearance.Mode.SOLID, 0xFFFFFFFF, null, Appearance.Alpha.OPAQUE);
        Rule keep = new Rule("Glass", Collections.singletonList("*glass*"), opaqueLook, true)
                .withLook(opaqueLook.withAlpha(Appearance.Alpha.KEEP), opaqueLook);
        CategorySettings opaque = new CategorySettings(opaqueLook, false, Collections.singletonList(keep));
        Config c = Config.defaults();
        Config withKeep = c.withProfile(c.activeProfile().with(Category.BLOCKS, opaque));
        assertEquals(withKeep, ConfigCodec.decode(ConfigCodec.encode(withKeep)));
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

    @Test
    void keysResolveForEveryEra() {
        assertEquals("key.keyboard.f6", Keys.byName("f6").minecraftName);
        assertEquals("key.keyboard.keypad.3", Keys.byName("KP_3").minecraftName);
        assertEquals(0x40, Keys.byName("F6").lwjgl2);
        assertNull(Keys.byName("NONE"));
    }
}
