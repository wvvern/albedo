package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.texture.Foliage;
import dev.albedo.core.texture.TextureKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Every rendering decision, answered from one immutable profile snapshot, thread-safe because texture loading runs on
 * worker threads on modern versions.
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

    /** Whether both policies give every texture the same look. */
    public boolean sameTextures(Policy other) {
        if (profile == null || other.profile == null) return profile == other.profile;
        return profile.configured().equals(other.profile.configured()) && profile.is(Toggle.FOLIAGE) == other.profile.is(Toggle.FOLIAGE);
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
        Rule group = s.ruleFor(key.id());
        if (group == null) {
            // Groups win; otherwise two world-wide decisions come before the category's look.
            if (c == Category.BLOCKS && !profile.is(Toggle.FOLIAGE) && Foliage.matches(key.id())) {
                // Invisible plants: zero alpha is skipped by cutout rendering on every version.
                return Appearance.of(Appearance.Mode.HIDDEN);
            }
            if (c == Category.SKY && overridesSky() && !key.id().contains("end_sky")) {
                // A flat sky has no sun, moon or stars; the End's sky texture takes the flat color instead.
                return Appearance.of(Appearance.Mode.HIDDEN);
            }
        }
        return s.lookOf(group);
    }

    public boolean cloudsHidden() {
        return profile != null && profile.get(Category.CLOUDS).appearance().mode() == Appearance.Mode.HIDDEN;
    }

    /** Should biome / hard-coded tints for this category be replaced by white? */
    public boolean removeTint(Category c) {
        return profile != null && profile.get(c).removeTint();
    }

    /** True when the sky (and fog, so the horizon blends) should be one flat color. */
    public boolean overridesSky() {
        return profile != null && profile.get(Category.SKY).appearance().mode() == Appearance.Mode.SOLID;
    }

    /** The flat sky color as {r, g, b} in 0..1 (what fog and clear-color APIs take), or null when not overridden. */
    public float[] skyRgb() {
        if (!overridesSky()) return null;
        int c = profile.get(Category.SKY).appearance().color();
        return new float[]{Colors.red(c) / 255f, Colors.green(c) / 255f, Colors.blue(c) / 255f};
    }

    /** Far above the brightness slider's maximum of 1.0: the lightmap ends up fully lit everywhere. */
    public static final float FULLBRIGHT_GAMMA = 16f;

    /** The brightness (gamma) setting the lightmap should use instead of the player's {@code setting}. */
    public double gamma(double setting) {
        return profile != null && profile.is(Toggle.FULLBRIGHT) ? FULLBRIGHT_GAMMA : setting;
    }

    /** False when ambient occlusion and per-face shading should be switched off. */
    public boolean shading() {
        return profile == null || profile.is(Toggle.SHADING);
    }

    public boolean shadows() {
        return profile == null || profile.is(Toggle.SHADOWS);
    }
}
