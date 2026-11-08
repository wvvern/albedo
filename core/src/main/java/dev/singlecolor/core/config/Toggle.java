package dev.singlecolor.core.config;

/** World-wide on/off switches of a profile. */
public enum Toggle {
    /** Light everything as if at noon. Applied live. */
    FULLBRIGHT("fullbright", false, false),
    /** Smooth lighting (ambient occlusion) and darker block sides. Baked into chunk meshes. */
    SHADING("shading", true, true),
    /** Grass, flowers, ferns, vines and other small plants. Baked into textures. */
    FOLIAGE("foliage", true, true),
    /** The round shadows under mobs and players. Applied live. */
    SHADOWS("shadows", true, false);

    public final String key;
    public final boolean defaultOn;
    /** Changing it needs a resource reload (textures or chunk meshes change). */
    public final boolean needsReload;

    Toggle(String key, boolean defaultOn, boolean needsReload) {
        this.key = key;
        this.defaultOn = defaultOn;
        this.needsReload = needsReload;
    }

    public static Toggle byKey(String key) {
        for (Toggle t : values()) if (t.key.equals(key)) return t;
        return null;
    }
}
