package dev.singlecolor.core;

import dev.singlecolor.core.config.Appearance;
import dev.singlecolor.core.config.Config;
import dev.singlecolor.core.config.ConfigCodec;
import dev.singlecolor.core.config.Presets;
import dev.singlecolor.core.texture.AtlasPaths;
import dev.singlecolor.core.texture.PixelTransformer;
import dev.singlecolor.core.texture.TextureClassifier;
import dev.singlecolor.core.texture.TextureKey;
import org.junit.jupiter.api.Test;

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
        assertEquals(Category.SKY, TextureClassifier.classify("minecraft:textures/environment/celestial/moon/full_moon.png").category());
        assertEquals(Category.BLOCK_ENTITIES, TextureClassifier.classify("minecraft:textures/entity/chest/normal.png").category());
        assertEquals(Category.ENTITIES, TextureClassifier.classify("minecraft:textures/entity/zombie/zombie.png").category());
        assertEquals(Category.PLAYERS, TextureClassifier.classify("minecraft:textures/entity/steve.png").category());
        assertEquals(Category.PLAYERS, TextureClassifier.classify("minecraft:textures/entity/player/wide/alex.png").category());
        assertEquals(Category.COLORMAPS, TextureClassifier.classify("minecraft:textures/colormap/grass.png").category());
        assertEquals(Category.OTHER, TextureClassifier.classify("minecraft:textures/gui/widgets.png").category());
        assertEquals("mymod:copper_ore", TextureClassifier.classify("MyMod", "textures/block/copper_ore.png").id());
    }

    @Test
    void solidKeepsShape() {
        PixelBuffer.Heap img = new PixelBuffer.Heap(2, 1, new int[]{0xFF123456, 0x00ABCDEF});
        PixelTransformer.apply(Appearance.solid(0xFFFFFFFF), img, null);
        assertEquals(0xFFFFFFFF, img.getArgb(0, 0));
        assertEquals(0x00FFFFFF, img.getArgb(1, 0));
    }

    @Test
    void solidOpaqueAndHiddenAndAverage() {
        PixelBuffer.Heap img = new PixelBuffer.Heap(2, 1, new int[]{0xFF000000, 0x00FFFFFF});
        PixelTransformer.apply(new Appearance(Appearance.Mode.SOLID, 0xFF808080, null, Appearance.Alpha.OPAQUE), img, null);
        assertEquals(0xFF808080, img.getArgb(1, 0));

        PixelTransformer.apply(Appearance.of(Appearance.Mode.HIDDEN), img, null);
        assertEquals(0, Colors.alpha(img.getArgb(0, 0)));

        PixelBuffer.Heap avg = new PixelBuffer.Heap(2, 1, new int[]{0xFF000000, 0xFFFFFFFF});
        assertEquals(0xFF7F7F7F, PixelTransformer.average(avg));
    }

    @Test
    void textureModeStretchesUserImage() {
        PixelBuffer.Heap user = new PixelBuffer.Heap(1, 1, new int[]{0xFFFF0000});
        PixelBuffer.Heap img = new PixelBuffer.Heap(2, 2, new int[]{0xFF000000, 0xFF000000, 0xFF000000, 0x00000000});
        PixelTransformer.apply(new Appearance(Appearance.Mode.TEXTURE, 0, "red.png", Appearance.Alpha.KEEP), img, n -> user);
        assertEquals(0xFFFF0000, img.getArgb(0, 0));
        assertEquals(0x00FF0000, img.getArgb(1, 1));
    }

    @Test
    void configRoundTripsAndRulesWin() {
        String json = "{\n" +
                "  // comments are allowed\n" +
                "  \"activeProfile\": \"mine\",\n" +
                "  \"profiles\": { \"mine\": {\n" +
                "    \"blocks\": { \"mode\": \"solid\", \"color\": \"#222\", \"removeTint\": true,\n" +
                "      \"rules\": [ { \"match\": [\"*glass*\", \"minecraft:ice\"], \"color\": \"#80FFFFFF\" },\n" +
                "                   { \"match\": \"*_ore\", \"mode\": \"original\" } ] },\n" +
                "    \"sky\": { \"mode\": \"solid\", \"color\": \"black\" },\n" +
                "  } }\n" +
                "}";
        Config c = ConfigCodec.decode(json);
        Policy p = Policy.of(c);
        assertEquals(0xFF222222, p.appearanceFor(TextureClassifier.classify("minecraft:block/stone")).color());
        assertEquals(0x80FFFFFF, p.appearanceFor(TextureClassifier.classify("minecraft:block/glass_pane_top")).color());
        assertTrue(p.appearanceFor(TextureClassifier.classify("minecraft:block/diamond_ore")).isOriginal());
        assertTrue(p.removeTint(Category.BLOCKS));
        assertTrue(p.overridesSky());
        assertEquals(0xFF000000, p.skyColor());
        assertEquals(Appearance.Mode.HIDDEN, p.appearanceFor(TextureClassifier.classify("minecraft:textures/environment/sun.png")).mode());
        assertEquals(Appearance.Mode.SOLID, p.appearanceFor(TextureClassifier.classify("minecraft:textures/environment/end_sky.png")).mode());
        assertEquals(0xFFFFFFFF, p.appearanceFor(TextureClassifier.classify("minecraft:textures/colormap/grass.png")).color());
        assertTrue(p.appearanceFor(TextureClassifier.classify("minecraft:textures/entity/zombie/zombie.png")).isOriginal());

        Config again = ConfigCodec.decode(ConfigCodec.encode(c));
        assertEquals(ConfigCodec.encode(c), ConfigCodec.encode(again));
    }

    @Test
    void presetsEncodeAndCycle() {
        Config c = new Config(true, "quake-white", "F6", "F7", Presets.all());
        Config decoded = ConfigCodec.decode(ConfigCodec.encode(c));
        assertEquals(c.profiles().keySet(), decoded.profiles().keySet());
        assertEquals("quake-black", c.nextProfileName());
        assertFalse(Policy.of(c.withEnabled(false)).enabled());
    }

    @Test
    void badConfigReportsLocation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"solid\", \"color\": \"nope\" } } } }"));
        assertTrue(e.getMessage().contains("x.blocks"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("{ \"profiles\": { \"x\": { \"bloks\": {} } } }"));
    }

    @Test
    void atlasRestoresStrippedFolders() {
        assertEquals("minecraft:particle/flame", AtlasPaths.spriteLocation("minecraft:textures/atlas/particles.png", "minecraft:flame"));
        assertEquals("minecraft:environment/celestial/sun", AtlasPaths.spriteLocation("minecraft:celestials", "minecraft:sun"));
        assertEquals("minecraft:block/stone", AtlasPaths.spriteLocation("minecraft:textures/atlas/blocks.png", "minecraft:block/stone"));
        assertEquals(Category.PARTICLES, TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:particles", "minecraft:flame")).category());
        assertEquals(Category.SKY, TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:celestials", "minecraft:moon/full_moon")).category());
        assertEquals(Category.BLOCK_ENTITIES, TextureClassifier.classify(AtlasPaths.spriteLocation("minecraft:paintings", "minecraft:kebab")).category());
    }

    @Test
    void keysHaveBothCodeFamilies() {
        assertEquals("key.keyboard.f6", Keys.byName("f6").minecraftName);
        assertEquals("key.keyboard.keypad.3", Keys.byName("KP_3").minecraftName);
        assertEquals(0x40, Keys.byName("F6").lwjgl2);
        assertNull(Keys.byName("NONE"));
    }
}
