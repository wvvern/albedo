package dev.albedo.core.texture;

import java.util.regex.Pattern;

/**
 * Small plants that only add visual noise: grass, ferns, flowers, saplings, vines, sugar cane, sea grass...
 * Hiding them means giving their block textures zero alpha, which cutout rendering skips on every version, so this
 * needs no version-specific code. Leaves are not included: they shape trees and have their own style.
 * Known trade-off: item models of these plants use the same textures, so their items are invisible too while hidden.
 * Names cover 1.8-1.12 ("tallgrass", "flower_*", "double_plant_*") and 1.13+.
 */
public final class Foliage {
    /** One pattern over the name after the namespace; ids from the classifier are already lower case. */
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
