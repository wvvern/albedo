package dev.singlecolor.core.config;

import dev.singlecolor.core.Glob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An id pattern list plus the appearance to use for ids that match any of them. */
public final class Rule {
    private final List<Glob> patterns;
    private final Appearance appearance;

    public Rule(List<String> patterns, Appearance appearance) {
        List<Glob> globs = new ArrayList<Glob>();
        for (String p : patterns) globs.add(Glob.compile(p));
        this.patterns = Collections.unmodifiableList(globs);
        this.appearance = appearance;
    }

    public boolean matches(String id) {
        for (Glob g : patterns) if (g.matches(id)) return true;
        return false;
    }

    public List<String> patternStrings() {
        List<String> out = new ArrayList<String>();
        for (Glob g : patterns) out.add(g.toString());
        return out;
    }

    public Appearance appearance() { return appearance; }
}
