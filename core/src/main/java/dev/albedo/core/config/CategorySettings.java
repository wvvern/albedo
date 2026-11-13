package dev.albedo.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * One category's immutable global look, switches and ordered groups (first match wins), where a group overrides the
 * global look for its members and a group set to {@link Appearance.Mode#ORIGINAL} excludes them.
 */
public final class CategorySettings {
    public static final CategorySettings UNTOUCHED = new CategorySettings(Appearance.ORIGINAL, false, false, Collections.<Rule>emptyList());
    /** Colors handed to new groups in turn, so each new group stands out. */
    private static final int[] GROUP_COLORS = {0xFF6D4C41, 0xFF43A047, 0xFF1E88E5, 0xFFE53935, 0xFFFDD835, 0xFF8E24AA};

    private final Appearance appearance;
    private final boolean removeTint;
    private final boolean onlyGroups;
    private final List<Rule> rules;
    /** The custom sky, which only the sky category with style {@link Appearance.Mode#LAYERS} uses. */
    private final SkyDesign sky;

    public CategorySettings(Appearance appearance, boolean removeTint, boolean onlyGroups, List<Rule> rules, SkyDesign sky) {
        this.appearance = appearance;
        this.removeTint = removeTint;
        this.onlyGroups = onlyGroups;
        this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
        this.sky = sky;
    }

    public CategorySettings(Appearance appearance, boolean removeTint, boolean onlyGroups, List<Rule> rules) {
        this(appearance, removeTint, onlyGroups, rules, SkyDesign.NONE);
    }

    public CategorySettings(Appearance appearance, boolean removeTint, List<Rule> rules) {
        this(appearance, removeTint, false, rules);
    }

    /** The first group matching {@code id}, or null. */
    public Rule ruleFor(String id) {
        for (Rule r : rules) if (r.matches(id)) return r;
        return null;
    }

    /** The look for a texture in {@code group}, or in no group when it is null, which is vanilla in "only groups". */
    public Appearance lookOf(Rule group) {
        if (group != null) return group.lookIn(appearance);
        return onlyGroups ? Appearance.ORIGINAL : appearance;
    }

    public Appearance appearance() { return appearance; }

    /**
     * Whether biome and fixed tints (grass, foliage, water) become white, so that the texture color shows.
     */
    public boolean removeTint() { return removeTint; }

    /** Whitelist scope: textures in no group stay vanilla. */
    public boolean onlyGroups() { return onlyGroups; }

    public List<Rule> rules() { return rules; }

    /** The category with a new global look, which the groups follow in the fields they do not set. */
    public CategorySettings withAppearance(Appearance a) {
        return new CategorySettings(a, removeTint, onlyGroups, rules, sky);
    }

    public CategorySettings withRemoveTint(boolean value) {
        return new CategorySettings(appearance, value, onlyGroups, rules, sky);
    }

    public CategorySettings withOnlyGroups(boolean value) {
        return new CategorySettings(appearance, removeTint, value, rules, sky);
    }

    public CategorySettings withRules(List<Rule> value) {
        return new CategorySettings(appearance, removeTint, onlyGroups, value, sky);
    }

    /** The custom sky layers, kept also while another style is selected so that a switch back finds them. */
    public SkyDesign sky() { return sky; }

    public CategorySettings withSky(SkyDesign value) {
        return new CategorySettings(appearance, removeTint, onlyGroups, rules, value);
    }

    /**
     * A new group, which takes the global look under "only groups" and else the next color of {@link #GROUP_COLORS}.
     */
    public Rule newGroup(String name, List<String> patterns) {
        if (onlyGroups) return new Rule(name, patterns, appearance, true);
        Appearance look = appearance.withMode(Appearance.Mode.SOLID).withColor(GROUP_COLORS[rules.size() % GROUP_COLORS.length]);
        return new Rule(name, patterns, look, EnumSet.of(Appearance.Field.MODE, Appearance.Field.COLOR));
    }

    public CategorySettings withRule(int index, Rule r) {
        List<Rule> next = new ArrayList<Rule>(rules);
        next.set(index, r);
        return withRules(next);
    }

    /** Inserts at {@code index}; earlier groups win, so 0 is the highest priority. */
    public CategorySettings withRuleAt(int index, Rule r) {
        List<Rule> next = new ArrayList<Rule>(rules);
        next.add(index, r);
        return withRules(next);
    }

    public CategorySettings withoutRule(int index) {
        List<Rule> next = new ArrayList<Rule>(rules);
        next.remove(index);
        return withRules(next);
    }

    public CategorySettings withRuleMoved(int index, int by) {
        List<Rule> next = new ArrayList<Rule>(rules);
        Collections.swap(next, index, index + by);
        return withRules(next);
    }

    /** Adds {@code pattern} to group {@code index}; the same pattern in any other group moves here. */
    public CategorySettings withPatternIn(int index, String pattern) {
        List<Rule> next = new ArrayList<Rule>();
        for (int i = 0; i < rules.size(); i++) {
            List<String> p = new ArrayList<String>(rules.get(i).patterns());
            p.remove(pattern);
            if (i == index) p.add(pattern);
            next.add(rules.get(i).withPatterns(p));
        }
        return withRules(next);
    }

    /**
     * A group whose look would mean nothing under the other scope ("vanilla" when only groups change, "global look"
     * when everything changes) blocks switching scope; returns it, or null if switching is safe.
     */
    public Rule blocksScopeSwitch() {
        for (Rule r : rules) {
            if (onlyGroups ? r.usesGlobalLook() : !r.usesGlobalLook() && r.lookIn(appearance).isOriginal()) return r;
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CategorySettings)) return false;
        CategorySettings c = (CategorySettings) o;
        return c.removeTint == removeTint && c.onlyGroups == onlyGroups && c.appearance.equals(appearance) && c.rules.equals(rules)
                && c.sky.equals(sky);
    }

    @Override
    public int hashCode() {
        return ((appearance.hashCode() * 31 + rules.hashCode()) * 31 + sky.hashCode()) * 31 + (removeTint ? 1 : 0) + (onlyGroups ? 2 : 0);
    }
}
