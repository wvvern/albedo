package dev.blockpainter.core;

import dev.blockpainter.core.config.Appearance;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a texture (or a non-texture visual such as the sky color) belongs to.
 * Every platform adapter maps what it renders onto these; nothing here knows about Minecraft classes.
 */
public enum Category {
    /** Terrain block textures (stone, grass, leaves, glass...). */
    BLOCKS,
    /** Water, lava and modded fluids. */
    FLUIDS,
    /** Things drawn with entity-style textures that are really scenery: chests, beds, signs, banners, paintings. */
    BLOCK_ENTITIES,
    /** Biome colormaps (grass/foliage). Flattened together with BLOCKS when tint removal is on. */
    COLORMAPS,
    /** Block breaking crack overlay. */
    BLOCK_BREAKING,
    ITEMS,
    /** Mobs, projectiles, armor, minecarts... */
    ENTITIES,
    /** Player skins and the default skins. */
    PLAYERS,
    PARTICLES,
    /** Sky color, fog color, sun, moon, stars, end sky. */
    SKY,
    CLOUDS,
    /** Rain and snow. */
    WEATHER,
    /** Everything unrecognised. Never touched unless a profile explicitly configures it. */
    OTHER;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The styles that mean something here. The sky is a color, not a texture; clouds can only be hidden (since 1.21.2
     * the game reads their shape, not their colors, from the texture).
     */
    public List<Appearance.Mode> supportedModes() {
        switch (this) {
            case SKY: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID);
            case CLOUDS: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.HIDDEN);
            case WEATHER: return Arrays.asList(Appearance.Mode.ORIGINAL, Appearance.Mode.SOLID, Appearance.Mode.HIDDEN);
            default: return Arrays.asList(Appearance.Mode.values());
        }
    }

    /** Whether groups of individual textures make sense (not for the sky, clouds and weather). */
    public boolean hasGroups() {
        return this != SKY && this != CLOUDS && this != WEATHER;
    }

    public static Category byKey(String key) {
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
