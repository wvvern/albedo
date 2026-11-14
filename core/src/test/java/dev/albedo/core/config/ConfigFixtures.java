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

    /** A Recolor look that keeps the alpha of the texture. */
    public static Appearance recolor(int color, int detail, int keepColors, int keepLight) {
        return new Appearance(Appearance.Mode.RECOLOR, color, Appearance.Alpha.KEEP, detail, keepColors, keepLight);
    }
}
