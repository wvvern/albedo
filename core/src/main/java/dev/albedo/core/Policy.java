package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.texture.Foliage;
import dev.albedo.core.texture.Layout;
import dev.albedo.core.texture.TextureKey;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every rendering decision, answered from one immutable profile snapshot, thread-safe because texture loading runs on
 * worker threads on modern versions.
 */
public final class Policy {
    public static final Policy DISABLED = new Policy(null);
    /** Far above the brightness slider's maximum of 1.0: the lightmap ends up fully lit everywhere. */
    static final float FULLBRIGHT_GAMMA = 16f;

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

    /** How the sky of the overworld is drawn. */
    public enum SkyStyle {
        VANILLA,
        /** One flat color for the sky and fog, with no sun, moon or stars. */
        FLAT,
        /** The custom sky of {@link #skyDesign()}. */
        LAYERS
    }

    /** Whether both policies give every texture the same look. */
    public boolean sameTextures(Policy other) {
        if (profile == null || other.profile == null) return profile == other.profile;
        return textureLooks(profile).equals(textureLooks(other.profile)) && profile.is(Toggle.FOLIAGE) == other.profile.is(Toggle.FOLIAGE);
    }

    /** Whether both policies give the chunk meshes the same shading and tints. */
    public boolean sameMeshes(Policy other) {
        return shading() == other.shading() && removeTint(Category.BLOCKS) == other.removeTint(Category.BLOCKS)
                && removeTint(Category.FLUIDS) == other.removeTint(Category.FLUIDS);
    }

    /** The categories of a profile with only the part of the sky layers that changes textures, so that a layer edit repaints nothing. */
    private static Map<Category, CategorySettings> textureLooks(Profile p) {
        Map<Category, CategorySettings> out = new EnumMap<Category, CategorySettings>(Category.class);
        out.putAll(p.configured());
        CategorySettings sky = out.get(Category.SKY);
        if (sky != null) out.put(Category.SKY, sky.withSky(sky.sky().texturesOnly()));
        return out;
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
        TextureKey sheet = group == null ? Layout.sheetOf(key) : null;
        // A group for a full 1.8.9 sheet, such as "particles", covers each tile that no group names.
        if (sheet != null) group = s.ruleFor(sheet.id());
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
            if (c == Category.SKY && skyStyle() == SkyStyle.LAYERS) {
                // The layers are drawn by the sky hooks, so the textures only show or hide the sun and moon.
                boolean hide = s.sky().celestial() == SkyDesign.Celestial.HIDE && !key.id().contains("end_sky");
                return hide ? Appearance.of(Appearance.Mode.HIDDEN) : Appearance.ORIGINAL;
            }
        }
        return s.lookOf(group);
    }

    public boolean cloudsHidden() {
        return profile != null && profile.get(Category.CLOUDS).appearance().mode() == Appearance.Mode.HIDDEN;
    }

    /** Whether the biome and fixed tints of this category become white. */
    public boolean removeTint(Category c) {
        return profile != null && profile.get(c).removeTint();
    }

    /** True when the sky, and the fog so that the horizon blends, is one flat color. */
    public boolean overridesSky() {
        return skyStyle() == SkyStyle.FLAT;
    }

    public SkyStyle skyStyle() {
        if (profile == null) return SkyStyle.VANILLA;
        switch (profile.get(Category.SKY).appearance().mode()) {
            case SOLID: return SkyStyle.FLAT;
            case LAYERS: return SkyStyle.LAYERS;
            default: return SkyStyle.VANILLA;
        }
    }

    /** The custom sky to draw, or null when the style is not {@link SkyStyle#LAYERS}. */
    public SkyDesign skyDesign() {
        return skyStyle() == SkyStyle.LAYERS ? profile.get(Category.SKY).sky() : null;
    }

    /** The flat sky color as {r, g, b} in 0..1 (what fog and clear-color APIs take), or null when not overridden. */
    public float[] skyRgb() {
        if (!overridesSky()) return null;
        int c = profile.get(Category.SKY).appearance().color();
        return new float[]{Colors.red(c) / 255f, Colors.green(c) / 255f, Colors.blue(c) / 255f};
    }

    /** The brightness (gamma) that the lightmap uses in place of the player's {@code setting}. */
    public double gamma(double setting) {
        return profile != null && profile.is(Toggle.FULLBRIGHT) ? FULLBRIGHT_GAMMA : setting;
    }

    /** False when ambient occlusion and per-face shading are off. */
    public boolean shading() {
        return profile == null || profile.is(Toggle.SHADING);
    }

    public boolean shadows() {
        return profile == null || profile.is(Toggle.SHADOWS);
    }

    /** False when the armor and elytra that players wear are hidden. */
    public boolean playerArmor() {
        return profile == null || profile.is(Toggle.ARMOR);
    }

    /** False when the capes that players wear are hidden. */
    public boolean playerCape() {
        return profile == null || profile.is(Toggle.CAPE);
    }
}
