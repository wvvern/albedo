package dev.albedo.core.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HueKeyTest {

    @Test
    void framesOfOneParticleShareAHue() {
        assertEquals(HueKey.turns("minecraft:generic_0"), HueKey.turns("minecraft:generic_7"));
    }

    @Test
    void everyPartOfABlockGetsOneHueOnEveryVersion() {
        assertEquals("grass_block", HueKey.of("minecraft:block/grass_block_top"));
        double grass = HueKey.turns("minecraft:block/grass_block_top");
        for (String id : new String[]{"minecraft:block/grass_block_side", "minecraft:block/grass_block_side_overlay",
                "minecraft:blocks/grass_top", "minecraft:blocks/grass_side_overlay", "minecraft:textures/block/grass_block_top.png"}) {
            assertEquals(grass, HueKey.turns(id), id);
        }
        assertEquals(HueKey.turns("minecraft:block/oak_log_top"), HueKey.turns("minecraft:blocks/log_oak_top"));
        assertEquals(HueKey.turns("minecraft:block/red_wool"), HueKey.turns("minecraft:blocks/wool_colored_red"));
        assertEquals(HueKey.turns("minecraft:block/stone_bricks"), HueKey.turns("minecraft:blocks/stonebrick"));
        assertEquals(HueKey.turns("minecraft:block/water_still"), HueKey.turns("minecraft:block/water_flow"));
        assertNotEquals(HueKey.turns("minecraft:block/grass_block_top"), HueKey.turns("minecraft:block/dirt"));
    }

    @Test
    void theKeyKeepsTheCaseOfTheId() {
        // The case is part of the hash input, so a lower-case key would change the hues.
        assertEquals("Stone", HueKey.of("minecraft:textures/block/Stone.png"));
        assertEquals("", HueKey.of(null));
    }

    @Test
    void stripKeepsTheFoldersAndTheCase() {
        assertEquals("block/Stone", ResourcePath.strip("mymod:textures/block/Stone.png"));
        assertEquals("particle/flame", ResourcePath.strip("particle/flame"));
    }
}
