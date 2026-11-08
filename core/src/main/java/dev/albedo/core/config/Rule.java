package dev.albedo.core.config;

import dev.albedo.core.Glob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group: a name, the texture ids or glob patterns it covers, and its look. Immutable.
 * A group with {@link #usesGlobalLook()} takes its category's look (useful when a category only changes its groups).
 */
public final class Rule {
    private final String name;
    private final List<String> patterns;
    private final List<Glob> globs;
    private final Appearance appearance;
    private final boolean globalLook;

    public Rule(String name, List<String> patterns, Appearance appearance, boolean globalLook) {
        this(name, Collections.unmodifiableList(new ArrayList<String>(patterns)), compile(patterns), appearance, globalLook);
    }

    /** Copies share the compiled patterns, so changing a look or name doesn't recompile anything. */
    private Rule(String name, List<String> patterns, List<Glob> globs, Appearance appearance, boolean globalLook) {
        this.name = name == null ? "" : name;
        this.patterns = patterns;
        this.globs = globs;
        this.appearance = appearance;
        this.globalLook = globalLook;
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

    public Appearance appearance() { return appearance; }

    public boolean usesGlobalLook() { return globalLook; }

    public Rule withName(String n) { return new Rule(n, patterns, globs, appearance, globalLook); }

    public Rule withPatterns(List<String> p) { return new Rule(name, p, appearance, globalLook); }

    public Rule withAppearance(Appearance a) { return new Rule(name, patterns, globs, a, false); }

    public Rule withGlobalLook() { return new Rule(name, patterns, globs, appearance, true); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Rule)) return false;
        Rule r = (Rule) o;
        return r.name.equals(name) && r.patterns.equals(patterns) && r.appearance.equals(appearance) && r.globalLook == globalLook;
    }

    @Override
    public int hashCode() { return (name.hashCode() * 31 + patterns.hashCode()) * 31 + appearance.hashCode(); }
}
