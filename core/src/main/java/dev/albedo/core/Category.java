package dev.albedo.core;

import dev.albedo.core.config.Appearance;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** What a texture (or a non-texture visual such as the sky color) belongs to, with no knowledge of Minecraft classes. */
public enum Category {
    /** Terrain block textures (stone, grass, leaves, glass...). */
    BLOCKS,
    FLUIDS,
    /** Things drawn with entity-style textures that are really scenery: chests, beds, bells, decorated pots. */
    BLOCK_ENTITIES,
    /** Things that carry text or pictures: signs, banners, paintings, maps and item frames. */
    DECORATIONS,
    /** Biome colormaps (grass/foliage), flattened together with BLOCKS when tint removal is on. */
    COLORMAPS,
    BLOCK_BREAKING,
    ITEMS,
    /** Mobs, projectiles, armor, minecarts... */
    ENTITIES,
    PLAYERS,
    PARTICLES,
    /** Sky color, fog color, sun, moon, stars, end sky. */
    SKY,
    CLOUDS,
    WEATHER,
    /** Everything unrecognised, which changes only when a profile explicitly configures it. */
    OTHER;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The styles that mean something here, where clouds can only be hidden because since 1.21.2 the game reads their
     * shape, not their colors, from the texture.
     */
    public List<Appearance.Mode> supportedModes() {
        switch (this) {
            case SKY: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID, Appearance.Mode.LAYERS);
            case CLOUDS: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.HIDDEN);
            case WEATHER: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID, Appearance.Mode.HIDDEN);
            default: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID, Appearance.Mode.RECOLOR,
                    Appearance.Mode.AVERAGE, Appearance.Mode.GRAYSCALE, Appearance.Mode.HIDDEN);
        }
    }

    /** Whether groups of individual textures make sense (not for the sky, clouds and weather). */
    public boolean hasGroups() {
        return this != SKY && this != CLOUDS && this != WEATHER;
    }

    /** Whether Bevel and Outline mean something here, which needs square tiles such as block faces. */
    public boolean hasEdgeEffects() {
        return this == BLOCKS || this == FLUIDS;
    }

    public static Category byKey(String key) {
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
