package dev.plainview.core;

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

    public static Category byKey(String key) {
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
