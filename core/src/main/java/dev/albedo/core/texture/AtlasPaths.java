package dev.albedo.core.texture;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Recovers the texture folder that several atlases strip from sprite names since 1.19.3 (the particle atlas names
 * {@code textures/particle/flame.png} just {@code minecraft:flame}), so that the classifier can tell a particle from
 * anything else.
 */
public final class AtlasPaths {
    private static final Map<String, String> PREFIXLESS = new HashMap<String, String>();

    static {
        PREFIXLESS.put("particles", "particle");
        PREFIXLESS.put("paintings", "painting");
        PREFIXLESS.put("celestials", "environment/celestial");
        PREFIXLESS.put("gui", "gui/sprites");
        PREFIXLESS.put("map_decorations", "map/decorations");
        PREFIXLESS.put("mob_effects", "mob_effect");
    }

    private AtlasPaths() {}

    /**
     * @param atlas  the atlas id or texture location, e.g. "minecraft:textures/atlas/particles.png" or "minecraft:particles"
     * @param sprite the sprite name, e.g. "minecraft:flame"
     * @return a location the classifier understands, e.g. "minecraft:particle/flame"
     */
    public static String spriteLocation(String atlas, String sprite) {
        String folder = PREFIXLESS.get(atlasName(atlas));
        if (folder == null) return sprite;
        int colon = sprite.indexOf(':');
        String ns = colon < 0 ? "minecraft" : sprite.substring(0, colon);
        String path = colon < 0 ? sprite : sprite.substring(colon + 1);
        if (path.startsWith(folder + "/")) return sprite;
        return ns + ":" + folder + "/" + path;
    }

    static String atlasName(String atlas) {
        String a = atlas.toLowerCase(Locale.ROOT);
        int colon = a.indexOf(':');
        if (colon >= 0) a = a.substring(colon + 1);
        int slash = a.lastIndexOf('/');
        if (slash >= 0) a = a.substring(slash + 1);
        if (a.endsWith(".png")) a = a.substring(0, a.length() - 4);
        return a;
    }
}
