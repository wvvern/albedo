package dev.blockpainter.core.config;

/** World-wide on/off switches of a profile. */
public enum Toggle {
    /** Light everything as if at noon. Read each frame. */
    FULLBRIGHT("fullbright", false),
    /** Smooth lighting (ambient occlusion) and darker block sides. Stored in chunk meshes. */
    SHADING("shading", true),
    /** Grass, flowers, ferns, vines and other small plants. Done with textures. */
    FOLIAGE("foliage", true),
    /** The round shadows under mobs and players. Read each frame. */
    SHADOWS("shadows", true);

    public final String key;
    public final boolean defaultOn;

    Toggle(String key, boolean defaultOn) {
        this.key = key;
        this.defaultOn = defaultOn;
    }

    public static Toggle byKey(String key) {
        for (Toggle t : values()) if (t.key.equals(key)) return t;
        return null;
    }
}
