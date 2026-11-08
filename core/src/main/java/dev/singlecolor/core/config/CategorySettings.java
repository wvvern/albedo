package dev.singlecolor.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The default appearance of one category, its per-id rules, and category-level switches. Immutable. */
public final class CategorySettings {
    public static final CategorySettings UNTOUCHED = new CategorySettings(Appearance.ORIGINAL, false, Collections.<Rule>emptyList());

    private final Appearance appearance;
    private final boolean removeTint;
    private final List<Rule> rules;

    public CategorySettings(Appearance appearance, boolean removeTint, List<Rule> rules) {
        this.appearance = appearance;
        this.removeTint = removeTint;
        this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
    }

    /** First matching rule wins; otherwise the category default. */
    public Appearance appearanceFor(String id) {
        for (Rule r : rules) if (r.matches(id)) return r.appearance();
        return appearance;
    }

    /** The first rule matching {@code id}, or null. */
    public Rule ruleFor(String id) {
        for (Rule r : rules) if (r.matches(id)) return r;
        return null;
    }

    public Appearance appearance() { return appearance; }

    /**
     * Replace biome/hard-coded tints (grass, foliage, water color...) with white so the flat texture color is
     * what actually shows.
     */
    public boolean removeTint() { return removeTint; }

    public List<Rule> rules() { return rules; }

    /** True if this category changes anything at all. */
    public boolean isActive() {
        if (removeTint || !appearance.isOriginal()) return true;
        for (Rule r : rules) if (!r.appearance().isOriginal()) return true;
        return false;
    }
}
