package dev.singlecolor.core;

import dev.singlecolor.core.config.Appearance;
import dev.singlecolor.core.config.CategorySettings;
import dev.singlecolor.core.config.Config;
import dev.singlecolor.core.config.Profile;
import dev.singlecolor.core.config.Toggle;
import dev.singlecolor.core.texture.Foliage;
import dev.singlecolor.core.texture.TextureKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Every rendering decision, answered from one immutable profile snapshot. Thread-safe: texture loading runs on
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

    /** For previews of a profile that isn't active. */
    public static Policy of(Profile profile) {
        return new Policy(profile);
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
        if (c == Category.COLORMAPS) {
            // Biome colormaps tint grass/leaves; flattening them is the renderer-independent half of tint removal.
            return removeTint(Category.BLOCKS) ? Appearance.solid(0xFFFFFFFF) : Appearance.ORIGINAL;
        }
        CategorySettings s = profile.get(c);
        if (s.ruleFor(key.id()) != null) return s.appearanceFor(key.id());
        if (c == Category.BLOCKS && !profile.is(Toggle.FOLIAGE) && Foliage.matches(key.id())) {
            // Invisible plants: zero alpha is skipped by cutout rendering on every version.
            return Appearance.of(Appearance.Mode.HIDDEN);
        }
        if (c == Category.SKY && overridesSky() && !key.id().contains("end_sky")) {
            // A flat sky has no sun, moon or stars; the End's sky texture takes the flat color instead.
            return Appearance.of(Appearance.Mode.HIDDEN);
        }
        return s.appearanceFor(key.id());
    }

    /** Should biome / hard-coded tints for this category be replaced by white? */
    public boolean removeTint(Category c) {
        return profile != null && profile.get(c).removeTint();
    }

    /** True when the sky (and fog, so the horizon blends) should be one flat color. */
    public boolean overridesSky() {
        return profile != null && profile.get(Category.SKY).appearance().mode() == Appearance.Mode.SOLID;
    }

    /** The flat sky color as opaque 0xAARRGGBB; only meaningful when {@link #overridesSky()}. */
    public int skyColor() {
        return profile == null ? 0 : profile.get(Category.SKY).appearance().color() | 0xFF000000;
    }

    public boolean fullbright() {
        return profile != null && profile.is(Toggle.FULLBRIGHT);
    }

    /** False when ambient occlusion and per-face shading should be switched off. */
    public boolean shading() {
        return profile == null || profile.is(Toggle.SHADING);
    }

    /** False when entities should cast no shadows. */
    public boolean shadows() {
        return profile == null || profile.is(Toggle.SHADOWS);
    }
}
