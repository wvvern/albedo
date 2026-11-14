package dev.albedo.core.texture;

import dev.albedo.core.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureClassifierTest {

    @Test
    void classifiesModernAndLegacyPaths() {
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:stone"), TextureClassifier.classify("minecraft:textures/block/stone.png"));
        assertEquals(new TextureKey(Category.BLOCKS, "minecraft:stone"), TextureClassifier.classify("minecraft:blocks/stone"));
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
        assertEquals("mymod:copper_ore", TextureClassifier.classify("MyMod:textures/block/copper_ore.png").id());
    }

    @Test
    void classifiesDownloadedSkinsCapesAndElytra() {
        assertEquals(new TextureKey(Category.PLAYERS, "minecraft:skins"), TextureClassifier.classify("minecraft:skins/0a1b2c"));
        assertEquals(new TextureKey(Category.PLAYERS, "minecraft:capes"), TextureClassifier.classify("minecraft:capes/0a1b2c"));
        assertEquals(new TextureKey(Category.PLAYERS, "minecraft:elytra"), TextureClassifier.classify("minecraft:elytra/0a1b2c"));
        assertTrue(TextureClassifier.isDownloaded("minecraft:skins/0a1b2c"));
        // Resource files and other namespaces are not downloads.
        assertFalse(TextureClassifier.isDownloaded("minecraft:textures/skins/0a1b2c.png"));
        assertFalse(TextureClassifier.isDownloaded("minecraft:skins/a.png"));
        assertFalse(TextureClassifier.isDownloaded("mymod:skins/0a1b2c"));
        assertEquals(Category.OTHER, TextureClassifier.classify("mymod:skins/0a1b2c").category());
        assertEquals(Category.OTHER, TextureClassifier.classify("minecraft:optifine/capeof/steve").category());
        // The skin layout: default and downloaded skins, not capes and elytra.
        assertTrue(isPlayerSkin("minecraft:skins/0a1b2c"));
        assertTrue(isPlayerSkin("minecraft:textures/entity/player/slim/alex.png"));
        assertTrue(isPlayerSkin("minecraft:textures/entity/steve.png"));
        assertFalse(isPlayerSkin("minecraft:capes/0a1b2c"));
        assertFalse(isPlayerSkin("minecraft:elytra/0a1b2c"));
        assertFalse(isPlayerSkin("minecraft:textures/entity/zombie/zombie.png"));
    }

    private static boolean isPlayerSkin(String location) {
        return TextureClassifier.isPlayerSkin(TextureClassifier.classify(location));
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
}
