package dev.albedo.core.ui;

import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Toggle;

import java.util.EnumMap;
import java.util.Map;

/** The words the settings screen uses for categories, styles and world toggles. */
final class Labels {
    private Labels() {}

    private static final class Words {
        final String name, members, example, description;

        /**
         * @param members what a group's patterns name, the label of its input ("Blocks", "Particles")
         * @param example a name to type in the group input
         */
        Words(String name, String members, String example, String description) {
            this.name = name;
            this.members = members;
            this.example = example;
            this.description = description;
        }
    }

    private static final Map<Category, Words> WORDS = new EnumMap<Category, Words>(Category.class);

    static {
        WORDS.put(Category.BLOCKS, new Words("Blocks", "Blocks", "dirt", "Terrain: stone, grass, leaves, glass..."));
        WORDS.put(Category.FLUIDS, new Words("Fluids", "Fluids", "water", "Water and lava"));
        WORDS.put(Category.BLOCK_ENTITIES, new Words("Block entities", "Textures", "chest", "Chests, beds, bells, decorated pots"));
        WORDS.put(Category.DECORATIONS, new Words("Decorations", "Textures", "banner", "Signs, banners, paintings, maps, item frames"));
        WORDS.put(Category.ITEMS, new Words("Items", "Items", "diamond_sword", "Item icons and held items"));
        WORDS.put(Category.ENTITIES, new Words("Mobs and entities", "Mobs", "creeper", "Mobs, projectiles, armor"));
        WORDS.put(Category.PLAYERS, new Words("Players", "Skins", "steve", "Default player skins"));
        WORDS.put(Category.PARTICLES, new Words("Particles", "Particles", "flame", "Smoke, flames, hits, splashes"));
        WORDS.put(Category.BLOCK_BREAKING, new Words("Mining cracks", "Stages", "destroy_stage", "The cracks on a block while you mine it"));
        WORDS.put(Category.SKY, new Words("Sky", "Textures", "sun", "Sky color. Also hides the sun, moon and stars."));
        WORDS.put(Category.CLOUDS, new Words("Clouds", "Textures", "clouds", "Clouds"));
        WORDS.put(Category.WEATHER, new Words("Weather", "Textures", "rain", "Rain and snow"));
    }

    private static Words words(Category c) {
        Words w = WORDS.get(c);
        return w != null ? w : new Words(c.key(), "Textures", "stone", "");
    }

    static String name(Category c) { return words(c).name; }

    static String members(Category c) { return words(c).members; }

    static String example(Category c) { return words(c).example; }

    static String description(Category c) { return words(c).description; }

    static String mode(Appearance.Mode m) {
        switch (m) {
            case ORIGINAL: return "Vanilla";
            case SOLID: return "Color";
            case RECOLOR: return "Recolor";
            case AVERAGE: return "Average";
            case GRAYSCALE: return "Gray";
            case TEXTURE: return "Image";
            case LAYERS: return "Layers";
            default: return "Hide";
        }
    }

    static String modeTip(Appearance.Mode m) {
        switch (m) {
            case ORIGINAL: return "Do not change";
            case SOLID: return "One flat color";
            case RECOLOR: return "Change the color and keep the detail";
            case AVERAGE: return "Each texture changes to its average color";
            case GRAYSCALE: return "Keep the detail and remove the color";
            case TEXTURE: return "A PNG image from config/albedo/textures";
            case LAYERS: return "Make your own sky from layers: gradients, stars, nebulas and filters";
            default: return "Do not show";
        }
    }

    static String toggle(Toggle t) {
        switch (t) {
            case FULLBRIGHT: return "Fullbright";
            case SHADING: return "Shading";
            case FOLIAGE: return "Foliage";
            default: return "Entity shadows";
        }
    }

    static String explain(Toggle t) {
        switch (t) {
            case FULLBRIGHT: return "Full light in all areas. No dark caves or night.";
            case SHADING: return "Smooth lighting and darker block sides.";
            case FOLIAGE: return "Grass, flowers, ferns and vines. Off hides them.";
            default: return "The round shadows below mobs and players.";
        }
    }
}
