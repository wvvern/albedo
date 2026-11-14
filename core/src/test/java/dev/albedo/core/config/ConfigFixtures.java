package dev.albedo.core.config;

import dev.albedo.core.Category;

/** Configs and looks that tests in more than one package build. */
public final class ConfigFixtures {
    private ConfigFixtures() {
    }

    /** The default config with {@code category} of the active profile set to {@code settings}. */
    public static Config with(Category category, CategorySettings settings) {
        Config c = Config.defaults();
        return c.withProfile(c.activeProfile().with(category, settings));
    }

    /** A solid look with still Chroma, whose hue the CPU paints, so that a test sees the colors without the GPU pass. */
    public static Appearance solid(int color, int bevel, int outline, int chroma) {
        return Appearance.solid(color).withEffects(bevel, outline, still(Chroma.of(chroma)));
    }

    /** The same Chroma at speed 0. */
    public static Chroma still(Chroma c) {
        return c.withOptions(0, c.saturation(), c.brightness());
    }

    /** A Recolor look that keeps the alpha of the texture. */
    public static Appearance recolor(int color, int detail, int keepColors, int keepLight) {
        return new Appearance(Appearance.Mode.RECOLOR, color, Appearance.Alpha.KEEP, detail, keepColors, keepLight);
    }
}
