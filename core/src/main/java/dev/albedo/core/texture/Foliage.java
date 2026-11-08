package dev.albedo.core.texture;

import java.util.regex.Pattern;

/**
 * Small plants that only add visual noise, hidden through zero alpha in their block textures, which also hides their
 * items because the item models use the same textures.
 */
public final class Foliage {
    /** Plant names of 1.8 to 1.12 and of 1.13 and later, without leaves, because leaves shape trees and have their own style. */
    private static final Pattern PLANTS = Pattern.compile("[^:]*:(?:"
            + "short_grass|tall_grass_.*|tallgrass|grass|.*fern.*|double_plant_.*|.*_sapling|sapling_.*|dead_?bush"
            + "|vine|sugar_cane|reeds|.*seagrass.*|dandelion|poppy|blue_orchid|allium|azure_bluet|.*_tulip|oxeye_daisy"
            + "|cornflower|lily_of_the_valley|wither_rose|torchflower|flower_.*|sunflower_.*|lilac_.*|rose_bush_.*|peony_.*"
            + "|pink_petals.*|wildflowers.*|leaf_litter.*|bush|firefly_bush.*|.*dry_grass.*|sweet_berry_bush.*"
            + "|glow_lichen|hanging_roots|lily_pad|waterlily|cactus_flower)");

    private Foliage() {}

    /** @param id a block texture id such as "minecraft:short_grass" */
    public static boolean matches(String id) {
        return PLANTS.matcher(id).matches();
    }
}
