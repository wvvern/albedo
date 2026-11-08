package dev.albedo.core.config;

import dev.albedo.core.Glob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable group: a name, the texture ids or glob patterns it covers, and the look fields it sets, where it takes
 * the other fields from its category.
 */
public final class Rule {
    private final String name;
    private final List<String> patterns;
    private final List<Glob> globs;
    /** The values of the fields in {@link #own}; the other fields have no meaning. */
    private final Appearance values;
    private final Set<Appearance.Field> own;

    /** A group that sets all fields of {@code appearance}, or none of them with {@code globalLook}. */
    public Rule(String name, List<String> patterns, Appearance appearance, boolean globalLook) {
        this(name, patterns, appearance, globalLook ? EnumSet.noneOf(Appearance.Field.class) : EnumSet.allOf(Appearance.Field.class));
    }

    public Rule(String name, List<String> patterns, Appearance values, Set<Appearance.Field> own) {
        this(name, Collections.unmodifiableList(new ArrayList<String>(patterns)), compile(patterns), values, own);
    }

    /** Copies share the compiled patterns, so changing a look or name doesn't recompile anything. */
    private Rule(String name, List<String> patterns, List<Glob> globs, Appearance values, Set<Appearance.Field> own) {
        this.name = name == null ? "" : name;
        this.patterns = patterns;
        this.globs = globs;
        this.values = values;
        this.own = Collections.unmodifiableSet(own.isEmpty() ? EnumSet.noneOf(Appearance.Field.class) : EnumSet.copyOf(own));
    }

    private static List<Glob> compile(List<String> patterns) {
        List<Glob> globs = new ArrayList<Glob>();
        for (String p : patterns) globs.add(Glob.compile(p));
        return globs;
    }

    public boolean matches(String id) {
        for (Glob g : globs) if (g.matches(id)) return true;
        return false;
    }

    /** The given name, or the first pattern for unnamed groups written by hand. */
    public String displayName() {
        return !name.isEmpty() ? name : patterns.isEmpty() ? "Group" : patterns.get(0);
    }

    public String name() { return name; }

    public List<String> patterns() { return patterns; }

    /** The look fields that this group sets for itself. */
    public Set<Appearance.Field> ownFields() { return own; }

    /** The group's look under a category look, which gives the fields that the group does not set. */
    public Appearance lookIn(Appearance category) {
        return category.with(own, values);
    }

    /** True when the group sets no field and so takes its category's look, which is useful when a category only changes its groups. */
    public boolean usesGlobalLook() { return own.isEmpty(); }

    public Rule withName(String n) { return new Rule(n, patterns, globs, values, own); }

    public Rule withPatterns(List<String> p) { return new Rule(name, p, values, own); }

    /** The group with the look {@code look}, where each field that differs from its look under {@code category} becomes its own. */
    public Rule withLook(Appearance look, Appearance category) {
        EnumSet<Appearance.Field> next = look.differences(lookIn(category));
        next.addAll(own);
        return new Rule(name, patterns, globs, look, next);
    }

    public Rule withGlobalLook() { return new Rule(name, patterns, globs, values, EnumSet.noneOf(Appearance.Field.class)); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Rule)) return false;
        Rule r = (Rule) o;
        return r.name.equals(name) && r.patterns.equals(patterns) && r.own.equals(own)
                && Appearance.ORIGINAL.with(own, r.values).equals(Appearance.ORIGINAL.with(own, values));
    }

    @Override
    public int hashCode() {
        return ((name.hashCode() * 31 + patterns.hashCode()) * 31 + own.hashCode()) * 31 + Appearance.ORIGINAL.with(own, values).hashCode();
    }
}
