package dev.albedo.core.ui;

import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.config.Transform;

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
        WORDS.put(Category.PLAYERS, new Words("Players", "Skins", "steve", "Player skins, capes and elytra, also downloaded ones"));
        WORDS.put(Category.PARTICLES, new Words("Particles", "Particles", "flame", "Smoke, flames, hits, splashes"));
        WORDS.put(Category.BLOCK_BREAKING, new Words("Mining cracks", "Stages", "destroy_stage", "The cracks on a block while you mine it"));
        WORDS.put(Category.SKY, new Words("Sky", "Textures", "sun", "One sky color, or your own sky from layers"));
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
            case LAYERS: return "Make your own sky from layers: gradients, stars, nebulas and filters";
            default: return "Do not show";
        }
    }

    static String kind(SkyLayer.Kind k) {
        switch (k) {
            case GRADIENT: return "Gradient";
            case GLOW: return "Glow";
            case NOISE: return "Noise";
            case FRACTAL: return "Fractal";
            case GALAXY: return "Galaxy";
            case STARS: return "Stars";
            case BLUR: return "Blur";
            case CRYSTALLIZE: return "Crystallize";
            case PIXELATE: return "Pixelate";
            case POSTERIZE: return "Posterize";
            case HUE_SHIFT: return "Hue shift";
            case BLOOM: return "Bloom";
            case SWIRL: return "Swirl";
            default: return "Vignette";
        }
    }

    static String kindTip(SkyLayer.Kind k) {
        switch (k) {
            case GRADIENT: return "Two colors from the horizon to the top of the sky";
            case GLOW: return "A band of light along the horizon, as at sunset";
            case NOISE: return "Soft clouds of two colors";
            case FRACTAL: return "Thin bright lines of gas, as in a nebula";
            case GALAXY: return "A spiral galaxy";
            case STARS: return "Small points of light";
            case BLUR: return "Makes the layers below soft";
            case CRYSTALLIZE: return "Breaks the layers below into crystal cells";
            case PIXELATE: return "Breaks the layers below into square cells";
            case POSTERIZE: return "Gives the layers below fewer color steps";
            case HUE_SHIFT: return "Changes the colors of the layers below";
            case BLOOM: return "Makes the light parts of the layers below glow";
            case SWIRL: return "Turns the layers below around a point";
            default: return "Makes the layers below darker away from a point";
        }
    }

    /** The name of a layer parameter, which depends on the kind (Amount is Radius for a blur). */
    static String param(SkyLayer.Kind k, SkyLayer.Param p) {
        switch (p) {
            case COLOR_1:
                return k == SkyLayer.Kind.GRADIENT ? "Low" : k == SkyLayer.Kind.NOISE || k == SkyLayer.Kind.FRACTAL ? "Dark"
                        : k == SkyLayer.Kind.GALAXY ? "Arms" : "Color";
            case COLOR_2:
                return k == SkyLayer.Kind.GRADIENT ? "High" : k == SkyLayer.Kind.GALAXY ? "Core" : "Light";
            case HEIGHT:
                return k == SkyLayer.Kind.GRADIENT ? "Middle" : k == SkyLayer.Kind.BLOOM ? "Threshold" : "Height";
            case SEED: return "Seed";
            default: return amount(k);
        }
    }

    private static String amount(SkyLayer.Kind k) {
        switch (k) {
            case GRADIENT: return "Softness";
            case GLOW: return "Width";
            case NOISE: return "Cover";
            case FRACTAL: return "Warp";
            case GALAXY: case SWIRL: return "Twist";
            case STARS: return "Count";
            case BLUR: return "Radius";
            case POSTERIZE: return "Levels";
            case HUE_SHIFT: return "Hue";
            default: return "Strength";
        }
    }

    static String blend(SkyLayer.Blend b) {
        switch (b) {
            case ADD: return "Add";
            case SCREEN: return "Screen";
            case MULTIPLY: return "Multiply";
            default: return "Normal";
        }
    }

    static String blendTip(SkyLayer.Blend b) {
        switch (b) {
            case ADD: return "Add the light of this layer to the layers below, for stars and glow";
            case SCREEN: return "Make the layers below lighter, but not brighter than white";
            case MULTIPLY: return "Make the layers below darker with the colors of this layer";
            default: return "Put this layer over the layers below";
        }
    }

    static String alpha(Appearance.Alpha a) {
        switch (a) {
            case BINARY: return "Cut-out";
            case OPAQUE: return "Solid";
            default: return "Keep";
        }
    }

    static String alphaTip(Appearance.Alpha a) {
        switch (a) {
            case BINARY: return "Make each part fully solid or fully transparent";
            case OPAQUE: return "Fill all holes. Leaves and glass become solid cubes.";
            default: return "Keep transparent parts (glass, leaves)";
        }
    }

    static String output(SkyLayer.Output o) {
        return o == SkyLayer.Output.MASK_ONLY ? "Mask only" : "In the sky";
    }

    static String outputTip(SkyLayer.Output o) {
        return o == SkyLayer.Output.MASK_ONLY ? "The layer does not show. Higher layers can use it as their mask."
                : "The layer shows in the sky";
    }

    static String maskMode(SkyMask.Mode m) {
        return m == SkyMask.Mode.DARK ? "Dark" : "Light";
    }

    static String maskModeTip(SkyMask.Mode m) {
        return m == SkyMask.Mode.DARK ? "Show where the mask layer is dark" : "Show where the mask layer is light";
    }

    static String source(SkyLayersEditor.PreviewSource s) {
        return s == SkyLayersEditor.PreviewSource.THIS_LAYER ? "This layer" : "All layers";
    }

    static String sourceTip(SkyLayersEditor.PreviewSource s) {
        return s == SkyLayersEditor.PreviewSource.THIS_LAYER ? "Only this layer, also when it is only a mask" : "The full sky";
    }

    /** The effects that are on, such as "Bevel 20%, Outline 50%", or "Off". */
    static String effects(Appearance a) {
        StringBuilder s = new StringBuilder();
        effect(s, "Bevel", a.bevel());
        effect(s, "Outline", a.outline());
        return s.length() == 0 ? "Off" : s.toString();
    }

    private static void effect(StringBuilder s, String name, int percent) {
        if (percent <= 0) return;
        if (s.length() > 0) s.append(", ");
        s.append(name).append(' ').append(percent).append('%');
    }

    static String axis(Transform.Axis a) {
        switch (a) {
            case YAW: return "Turn";
            case PITCH: return "Tilt";
            case ROLL: return "Roll";
            default: return "Size";
        }
    }

    static String axisTip(Transform.Axis a) {
        switch (a) {
            case YAW: return "Turn: the compass direction of the layer center. 0 is north, 90 is east.";
            case PITCH: return "Tilt: the height of the layer center above the horizon. 90 is the top of the sky.";
            case ROLL: return "Roll: turns the layer around its center.";
            default: return "Size: 100% is the normal size.";
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
