package dev.albedo.core.texture;

/** The name that gives a texture its Chroma hue, and the place of that hue in the hues that Chroma uses. */
final class HueKey {
    /** Parts of a block that end its texture name and must not change its hue. */
    private static final String[] PART_SUFFIXES = {"_overlay", "_side", "_top", "_bottom", "_front", "_back", "_end", "_inner",
        "_outer", "_still", "_flow", "_flowing", "_particle", "_normal", "_on"};

    /** Legacy (1.8.9) block names whose modern name has a different base, as pairs of old and new name. */
    private static final String[][] LEGACY_NAMES = {{"grass", "grass_block"}, {"stonebrick", "stone_bricks"}, {"brick", "bricks"},
        {"cobblestone_mossy", "mossy_cobblestone"}, {"ice_packed", "packed_ice"}, {"tallgrass", "short_grass"},
        {"flower_rose", "poppy"}, {"pumpkin_face_off", "carved_pumpkin"}};

    /** Legacy prefixes that the modern name has as a suffix, for example {@code log_oak} and {@code oak_log}. */
    private static final String[][] LEGACY_PREFIXES = {{"log_", "_log"}, {"planks_", "_planks"}, {"wool_colored_", "_wool"},
        {"leaves_", "_leaves"}};

    private HueKey() {}

    /** The place of the hue of an id in the hues that Chroma uses, from 0 to 1. */
    static double turns(String id) {
        String s = of(id);
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return ((h >>> 8) & 0xFFFF) / (double) 0x10000;
    }

    /** The name with no folder, frame number or part suffix, and the modern block name. */
    static String of(String id) {
        String s = ResourcePath.strip(id == null ? "" : id);
        if (s.startsWith("block/")) s = s.substring("block/".length());
        else if (s.startsWith("blocks/")) s = s.substring("blocks/".length());
        int end = s.length();
        while (end > 0 && Character.isDigit(s.charAt(end - 1))) end--;
        if (end < s.length() && end > 1 && s.charAt(end - 1) == '_') s = s.substring(0, end - 1);
        for (boolean cut = true; cut;) {
            cut = false;
            for (String suffix : PART_SUFFIXES) {
                if (s.length() > suffix.length() && s.endsWith(suffix)) {
                    s = s.substring(0, s.length() - suffix.length());
                    cut = true;
                }
            }
        }
        for (String[] p : LEGACY_PREFIXES) {
            if (s.startsWith(p[0]) && s.length() > p[0].length()) return s.substring(p[0].length()) + p[1];
        }
        for (String[] n : LEGACY_NAMES) {
            if (s.equals(n[0])) return n[1];
        }
        return s;
    }
}
