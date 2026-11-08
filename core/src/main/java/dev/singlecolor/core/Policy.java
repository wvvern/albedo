package dev.singlecolor.core;

import dev.singlecolor.core.config.Appearance;
import dev.singlecolor.core.config.CategorySettings;
import dev.singlecolor.core.config.Config;
import dev.singlecolor.core.config.Profile;
import dev.singlecolor.core.config.Rule;
import dev.singlecolor.core.texture.TextureKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Every rendering decision, answered from one immutable config snapshot. Thread-safe: texture loading runs on
 * worker threads on modern versions. A new Policy is built whenever the config changes.
 */
public final class Policy {
    public static final Policy DISABLED = new Policy(null);

    private final Profile profile; // null when disabled
    private final ConcurrentHashMap<TextureKey, Appearance> cache = new ConcurrentHashMap<TextureKey, Appearance>();

    private Policy(Profile profile) {
        this.profile = profile;
    }

    public static Policy of(Config config) {
        return config.enabled() ? new Policy(config.activeProfile()) : DISABLED;
    }

    public boolean enabled() {
        return profile != null;
    }

    public Appearance appearanceFor(TextureKey key) {
        if (profile == null) return Appearance.ORIGINAL;
        Appearance cached = cache.get(key);
        if (cached != null) return cached;
        Appearance a = resolve(key);
        cache.put(key, a);
        return a;
    }

    private Appearance resolve(TextureKey key) {
        Category c = key.category();
        CategorySettings s = profile.get(c);
        Rule rule = s.ruleFor(key.id());
        if (rule != null) return rule.appearance();

        if (c == Category.COLORMAPS && !profile.configured().containsKey(Category.COLORMAPS)) {
            // Biome colormaps tint grass/leaves; flattening them is the version-independent half of tint removal.
            return profile.get(Category.BLOCKS).removeTint() ? Appearance.solid(0xFFFFFFFF) : Appearance.ORIGINAL;
        }
        if (c == Category.SKY && s.appearance().mode() == Appearance.Mode.SOLID && !key.id().contains("end_sky")) {
            // A flat sky has no sun, moon or stars; the End's sky texture takes the flat color instead.
            return Appearance.of(Appearance.Mode.HIDDEN);
        }
        return s.appearance();
    }

    /** Should biome / hard-coded tints for this category be replaced by white? */
    public boolean removeTint(Category c) {
        return profile != null && profile.get(c).removeTint();
    }

    /** True when the sky (and fog, so the horizon blends) should be one flat color. */
    public boolean overridesSky() {
        return profile != null && profile.get(Category.SKY).appearance().mode() == Appearance.Mode.SOLID;
    }

    /** The flat sky color as 0xAARRGGBB; only meaningful when {@link #overridesSky()}. */
    public int skyColor() {
        return profile == null ? 0 : profile.get(Category.SKY).appearance().color() | 0xFF000000;
    }

    /** Whether the category changes anything (adapters can skip hooks cheaply). */
    public boolean affects(Category c) {
        return profile != null && profile.get(c).isActive();
    }
}
