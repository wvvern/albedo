package dev.plainview.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One category's global look, its ordered groups (first match wins) and switches. Immutable.
 *
 * <p>Two scopes keep the hierarchy unambiguous:
 * <ul>
 *   <li>everything (default): the global look applies to every texture; a group overrides it for its members, and a
 *       group set to {@link Appearance.Mode#ORIGINAL} excludes them (a blacklist);</li>
 *   <li>{@link #onlyGroups()}: only group members change (a whitelist); a group can take the global look.</li>
 * </ul>
 */
public final class CategorySettings {
    public static final CategorySettings UNTOUCHED = new CategorySettings(Appearance.ORIGINAL, false, false, Collections.<Rule>emptyList());

    private final Appearance appearance;
    private final boolean removeTint;
    private final boolean onlyGroups;
    private final List<Rule> rules;

    public CategorySettings(Appearance appearance, boolean removeTint, boolean onlyGroups, List<Rule> rules) {
        this.appearance = appearance;
        this.removeTint = removeTint;
        this.onlyGroups = onlyGroups;
        this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
    }

    public CategorySettings(Appearance appearance, boolean removeTint, List<Rule> rules) {
        this(appearance, removeTint, false, rules);
    }

    /** The first group matching {@code id}, or null. */
    public Rule ruleFor(String id) {
        for (Rule r : rules) if (r.matches(id)) return r;
        return null;
    }

    /** What applies to {@code id}: its group's look, the global look, or vanilla for unlisted ids in "only groups". */
    public Appearance appearanceFor(String id) {
        Rule r = ruleFor(id);
        if (r != null) return r.usesGlobalLook() ? appearance : r.appearance();
        return onlyGroups ? Appearance.ORIGINAL : appearance;
    }

    public Appearance appearance() { return appearance; }

    /**
     * Replace biome/hard-coded tints (grass, foliage, water color...) with white so the flat texture color is
     * what actually shows.
     */
    public boolean removeTint() { return removeTint; }

    /** Whitelist scope: textures in no group stay vanilla. */
    public boolean onlyGroups() { return onlyGroups; }

    public List<Rule> rules() { return rules; }

    /**
     * Group fields equal to the old global look follow the new one. That is what the JSON already means (a group only
     * stores fields that differ from its category), so e.g. the "solid leaves" group keeps the blocks color.
     */
    public CategorySettings withAppearance(Appearance a) {
        List<Rule> next = new ArrayList<Rule>();
        for (Rule r : rules) next.add(r.usesGlobalLook() ? r : r.withAppearance(inherit(r.appearance(), appearance, a)));
        return new CategorySettings(a, removeTint, onlyGroups, next);
    }

    public CategorySettings withRemoveTint(boolean value) {
        return new CategorySettings(appearance, value, onlyGroups, rules);
    }

    public CategorySettings withOnlyGroups(boolean value) {
        return new CategorySettings(appearance, removeTint, value, rules);
    }

    public CategorySettings withRules(List<Rule> value) {
        return new CategorySettings(appearance, removeTint, onlyGroups, value);
    }

    /**
     * A group whose look would mean nothing under the other scope ("vanilla" when only groups change, "global look"
     * when everything changes) blocks switching scope; returns it, or null if switching is safe.
     */
    public Rule blocksScopeSwitch() {
        for (Rule r : rules) {
            if (onlyGroups ? r.usesGlobalLook() : !r.usesGlobalLook() && r.appearance().isOriginal()) return r;
        }
        return null;
    }

    private static Appearance inherit(Appearance rule, Appearance oldParent, Appearance newParent) {
        return new Appearance(
                rule.mode() == oldParent.mode() ? newParent.mode() : rule.mode(),
                rule.color() == oldParent.color() ? newParent.color() : rule.color(),
                eq(rule.texture(), oldParent.texture()) ? newParent.texture() : rule.texture(),
                rule.alpha() == oldParent.alpha() ? newParent.alpha() : rule.alpha());
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CategorySettings)) return false;
        CategorySettings c = (CategorySettings) o;
        return c.removeTint == removeTint && c.onlyGroups == onlyGroups && c.appearance.equals(appearance) && c.rules.equals(rules);
    }

    @Override
    public int hashCode() { return (appearance.hashCode() * 31 + rules.hashCode()) * 31 + (removeTint ? 1 : 0) + (onlyGroups ? 2 : 0); }
}
